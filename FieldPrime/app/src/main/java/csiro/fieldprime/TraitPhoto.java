/*
 * TraitPhoto:
 * Photos are uploaded separately from the datums to a URL provided by the server.
 * The URL of the uploaded file is sent as the datum value.
 */

package csiro.fieldprime;

import static csiro.fieldprime.DbNames.DM_GPS_LAT;
import static csiro.fieldprime.DbNames.DM_GPS_LONG;
import static csiro.fieldprime.DbNames.DM_ID;
import static csiro.fieldprime.DbNames.DM_NODE_ID;
import static csiro.fieldprime.DbNames.DM_SAVED;
import static csiro.fieldprime.DbNames.DM_TIMESTAMP;
import static csiro.fieldprime.DbNames.DM_USERID;
import static csiro.fieldprime.DbNames.DM_VALUE;
import static csiro.fieldprime.DbNames.TABLE_DATUM;
import static csiro.fieldprime.DbNames.TABLE_TRAITPHOTO;
import static csiro.fieldprime.DbNames.TI_DAYCREATED;
import static csiro.fieldprime.DbNames.TI_SAMPNUM;
import static csiro.fieldprime.DbNames.TI_SEQNUM;
import static csiro.fieldprime.DbNames.TTP_TRAITID;
import static csiro.fieldprime.DbNames.TTP_URL;
import static csiro.fieldprime.Trait.Datatype.T_PHOTO;

import java.io.File;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

public class TraitPhoto extends Trait {
	public static final String PHOTO_URL = "photoUploadURL";  // json item name
	String mPhotoUploadURL;
	
	
	@Override
	public Datatype getType() {
		return T_PHOTO;
	}
	@Override
	Datum CreateDatum(TraitInstance ti, Node tu) {
		return new DatumPhoto(ti, tu);
	}
	@Override
	boolean WriteDatumJSON(JSONObject jdatum, Cursor csr)  throws JSONException {
		// The value to write to the server should probably be the full URL of the uploaded photo,
		// which should probably be a file name concatenated to a URL provided by the server
		jdatum.put(DM_VALUE, csr.getString(csr.getColumnIndex(DM_VALUE)));
		return true;
	}
	
	/*
	 * InitTraitJSON()
	 * Need to get and store URLs for photo upload, and possibly access URL for the datum value..
	 */
	@Override
	Result initTraitJSON(JSONObject jtrt, Trial trl, boolean update) throws JSONException {
		if (jtrt.isNull(PHOTO_URL)) return new Result(false, "Missing photo upload URL");
		mPhotoUploadURL = jtrt.getString(PHOTO_URL);  
		
		// Save the upload url to db:
		ContentValues values = new ContentValues();
		values.put(TTP_TRAITID, getId());
		values.put(TTP_URL, mPhotoUploadURL);
		if (g_db().insertWithOnConflict(TABLE_TRAITPHOTO, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
			return new Result(false, "Error saving photo url to db");

		return new Result();
	}
	
	
	/*
	 * UploadTraitInstance()
	 * The photos have to be uploaded separately from, and before, the data.
	 * NB, it is expected that this is called from an asynchronous thread (not the UI thread).
	 * MFK Ideally we would have an "uploaded" flag on individual photo datums to allow
	 * the option of only uploading photos that haven't already been uploaded - eg when
	 * retrying after some, but not all, photos failed, or when new photos have been added to
	 * an existing score set.
	 * 
	 * Method:
	 * First an traitInstance json is sent, without data, to force creation of the ti on the server.
	 * Then for each datum, either the photos is sent, with the metadata in parameters sufficient
	 * for the server to create the datum. Or - if the value is NA - a traitInstance json is sent
	 * containing just the (NA) datum.
MFK - use saved flag!
	 */
	@Override
	public Result uploadTraitInstance(TraitInstance ti) throws JSONException {
		// Upload the photos as required.
		// Dirty bit would be good here (on datums), as this may be slow
		int MAX_PHOTOS_FAILED = 4;      // Give up after this many photos have failed to upload
		int NUM_TRIES = 3;              // Try this many times for each photo.
		int numPhotosFailed = 0;
		String failMsg = "";
		
		/*
		 * The issue is that to upload a photos there are 2 separate items: The
		 * datum record and the photos itself. We want an atomic operation, i.e.
		 * the photo is saved on the server iff the datum is created. To achieve this we
		 * want both pieces to be sent up in the same HTTP transaction.
		 * The datum metadata will go with each photo.
		 */
		
		/*
		 * Upload the photos:
		 * MFK note need to make unsaved optional, the alternative being
		 * ccr = ti.getDatumCursor();
		 */
		Cursor ccr = null;
		try {		
			ccr = ti.getUnsavedDatumCursor();
			if (ccr.moveToFirst()) {
				do {
					// Value field. NA indicated by absence of value
					if (!ccr.isNull(ccr.getColumnIndex(DM_VALUE))) {
						String val = ccr.getString(ccr.getColumnIndex(DM_VALUE));
						int nodeId = ccr.getInt(ccr.getColumnIndex(DM_NODE_ID));
						int datId = ccr.getInt(ccr.getColumnIndex(DM_ID));
						
						/*
						 *  upload the photo:
						 *  Need to add seqNum and sampleNum to the URL:
						 *  Note deviation from pure HATEOAS here in that we have
						 *  to add the local seq and sample num. But this info is
						 *  generated locally, so hard to avoid this..
						 *  Note there is also other out-of-band info in the upload
						 *  file name, which contains the node id and traitInstance id.
						 */
						// Note we add userid, gps, timestamp.. so the server can create the datum record.
						// We also add the traitInstance creation date so the server can create the traitInstance
						// if it doesn't already exist.
						String url = Server.addParametersToUrl(Server.addStandardOutOfBandParams(mPhotoUploadURL),
								new String [] {TI_SEQNUM, TI_SAMPNUM, DM_TIMESTAMP, DM_USERID,
										DM_GPS_LAT, DM_GPS_LONG, DM_NODE_ID, TI_DAYCREATED},
								new String[] {
									Long.toString(ti.getExportSeqNum()),
									Integer.toString(ti.getSampleNum()),
									Long.toString(ccr.getLong(ccr.getColumnIndex(DM_TIMESTAMP))),
									ccr.getString(ccr.getColumnIndex(DM_USERID)),
									Double.toString(ccr.getDouble(ccr.getColumnIndex(DM_GPS_LAT))),
									Double.toString(ccr.getDouble(ccr.getColumnIndex(DM_GPS_LONG))),
									Integer.toString(nodeId),
									Integer.toString(ti.getCreationDate())
									});
						int triesLeft;
						Result photoUploadRes = null;
						for (triesLeft = NUM_TRIES; triesLeft > 0; --triesLeft) {	
							photoUploadRes = Server.uploadFile(url, LocalPathFromStoredValue(val),
									"" + ccr.getInt(ccr.getColumnIndex(DM_NODE_ID)) + ".jpg");
							if (photoUploadRes.good())
								break;						
						}
						if (triesLeft == 0) {
							++numPhotosFailed;
							failMsg += photoUploadRes.errMsg() + "\n";  // add the last error message
						} else { // Flag the datum as saved (NB this is cleared whenever data changed)
							ContentValues values = new ContentValues();
							values.put(DM_SAVED, 1);
							int numAffected = g_db().update(TABLE_DATUM, values,
									String.format("%s = %d", DM_ID, datId), null);
						}
					} else {
						// NA case, upload trait instance datums containing just this datum:
						JSONObject jti = createEmptyTraitInstanceJSON(ti);
						JSONArray jdata = new JSONArray();
						JSONObject jdatum = new JSONObject();
						jdatum.put(DM_NODE_ID, ccr.getInt(ccr.getColumnIndex(DM_NODE_ID)));
						jdatum.put(DM_TIMESTAMP, ccr.getLong(ccr.getColumnIndex(DM_TIMESTAMP)));
						jdatum.put(DM_GPS_LONG, ccr.getDouble(ccr.getColumnIndex(DM_GPS_LONG)));
						jdatum.put(DM_GPS_LAT, ccr.getDouble(ccr.getColumnIndex(DM_GPS_LAT)));
						jdatum.put(DM_USERID, ccr.getString(ccr.getColumnIndex(DM_USERID)));
						// Value field - NA indicated by absence of value:
						jdata.put(jdatum);
						jti.put("data", jdata);
						Result res = Server.uploadJSON(jti, getUploadURL());
						if (res.bad()) {
							++numPhotosFailed;
							failMsg += "NA upload failed: " + res.errMsg() + "\n";  // add the last error message
						}
					}
				} while (numPhotosFailed < MAX_PHOTOS_FAILED && ccr.moveToNext());
			}
		} finally { if (ccr != null) ccr.close(); }
		
		/*
		 * Return result 
		 */
		if (numPhotosFailed > 0) {
			return new Result(false, "" + numPhotosFailed + " could not be uploaded\n" + failMsg);
		} else
			return new Result();
	}

	@Override
	public String repSetStats(RepSet repSet) {
		return "";
	}
	
	@Override
	void SetFromDB(Trial trl) {
		// Retrieve the upload URL from db:
		String qry = String.format("select %s from %s where %s = %d", TTP_URL, TABLE_TRAITPHOTO, TTP_TRAITID, getId());
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(qry, null);
			if (ccr.moveToFirst())
				mPhotoUploadURL = ccr.getString(ccr.getColumnIndex(TTP_URL));
			return;
		} finally { if (ccr != null) ccr.close(); }
	}
	
	@Override
	boolean SupportsBluetoothScoring() {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * LocalPathFromStoredValue() Static version
	 * We need to get the path from the value from elsewhere, without a datum object.
	 */
	static String LocalPathFromStoredValue(String storedValue) {
		return Globals.g.getAppRootDir() /*+ GetTrial().getName()  + "/" */ + storedValue;
	}

	
	/*
	 * DeleteTraitInstance()
	 * Overriding default - we need to remove the photo files.
	 */
	@Override
	public Result DeleteTraitInstance(TraitInstance ti) {
		// Remove any photo files:
		final File folder = new File(ti.getTrial().storageDir());
		String prefix = "" + ti.id() + "_";
		for (File f: folder.listFiles())
		    if(f.getName().startsWith(prefix))
		        f.delete();
		return super.DeleteTraitInstance(ti);
	}

	class DatumPhoto extends Datum {
		// CONSTANTS: ========================================================================================================

		// DATA-STATIC: ======================================================================================================

		// DATA-INSTANCE: ====================================================================================================
		private String mValue;
		private String mCachedValue;
		private ImageView mImage;
		private Button mButton;
		private LinearLayout mLayout;

		// METHODS-INSTANCE: =================================================================================================

		public DatumPhoto(Trial.TraitInstance ti, Node tu) {
			super(ti, tu);
		}
		
		@Override
		protected void WriteValue(String valKey, ContentValues values) {
			values.put(valKey, mValue);
		}

		@Override
		protected String GetValidValueAsString() {
			return mValue;
		}

		@Override
		protected void SetValueFromDB(String valKey, Cursor crs) {
			mValue = crs.getString(crs.getColumnIndex(valKey));
		}

		@Override
		protected View scoreView() {
			mLayout = new LinearLayout(mCtx); // need enclosing layout for some reason, it seems otherwise only the first addition to vg is shown
			mLayout.setOrientation(LinearLayout.VERTICAL);
			mLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.MATCH_PARENT));

			// Add button to take a picture:
			mButton = new Button(mCtx);
			Util.SetupScreenWidthTextView(mButton, false);
			mButton.setText(isNA() ? "NA - Take a Picture" : "Take a Picture");
			mButton.setGravity(Gravity.LEFT); // don't need this now?
			mButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (!ActTrial.mActTrial.setDatumAwaitingPhotoResult(DatumPhoto.this)) {
						Util.msg("Cannot take photo now");
						return;
					}
					Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
					Uri fileUri = GetPhotoUri();
					intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, fileUri); // set the image file name
					ActTrial.mActTrial.startActivityForResult(intent, ActTrial.ARC_CAPTURE_IMAGE);
					//ActTrial.mActTrial.recreate();
				}
			});
			mLayout.addView(mButton);

			// Add image view for displaying picture:  MFK - size, orientation?
			mImage = displayImage();
			mLayout.addView(mImage);
			return mLayout;
		}
		
		/*
		 * localPathFromStoredValue()
		 * Get local file path to the photo from the stored database value.
		 */
		String localPathFromStoredValue() {
			return TraitPhoto.LocalPathFromStoredValue(mValue);
		}

		private int getCameraPhotoOrientation(String imagePath) {
		    int rotate = 0;
		    try {
		        File imageFile = new File(imagePath);

		        ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
		        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

		        switch (orientation) {
		        case ExifInterface.ORIENTATION_ROTATE_270:
		            rotate = 270;
		            break;
		        case ExifInterface.ORIENTATION_ROTATE_180:
		            rotate = 180;
		            break;
		        case ExifInterface.ORIENTATION_ROTATE_90:
		            rotate = 90;
		            break;
		        }
		    } catch (Exception e) {
		        e.printStackTrace();
		    }
		    return rotate;
		}
		
		
		
		/*
		 * displayImage()
		 * If value is present, put it in the image view (assumed to be present).
		 */
		private ImageView displayImage() {
			ImageView iv = new ImageView(mCtx);
			if (hasDBValue() && !isNA()) {
				String imagePath = localPathFromStoredValue();
				int rotation = getCameraPhotoOrientation(imagePath);
				//Util.toast("rotation: " + rotation);
				
				BitmapFactory.Options options=new BitmapFactory.Options();
				options.inSampleSize = 8;
				
				//Bitmap preview_bitmap=BitmapFactory.decodeStream(is,null,options);
				Bitmap bm = BitmapFactory.decodeFile(localPathFromStoredValue(), options);
				
				// rotate and scale here, but note we've already scaled - to save mem I think
				// http://stackoverflow.com/questions/8981845/android-rotate-image-in-imageview-by-an-angle
				
				iv.setRotation(rotation);
				iv.setImageBitmap(bm);
			} else {
				iv.setImageResource(0);
				//mImage.setImageDrawable(null);
				iv.invalidate();
			}
			return iv;
		}

		@Override
		protected void drawSavedScore() {
			Redisplay(); // it seems that the only way to get the imageView to refresh is to recreate everything..
		}

		/*
		 * PhotoFileBasename()
		 * Returns filename for photo - constructed from the traitInstance id and node id.
		 */
		private String PhotoFileBasename() {
			return mTraitInst.getId() + "_" + mNodeId + ".jpg";
		}

		/*
		 * PhotoAbsolutePath()
		 * Returns file path for photo
		 * The directory is the trial storage dir, and the name has the traitInstance id and node id.
		 */
		private String PhotoAbsolutePath() {
			return getTrial().storageDir() + "/" + PhotoFileBasename();
		}

		private Uri GetPhotoUri() {
			File mediaFile = new File(PhotoAbsolutePath());
			return Uri.fromFile(mediaFile);
		}

		private void setValue(String s) {
			mValue = s;
			SetNA(false);
		}

		private void CacheValue(String s) {
			mCachedValue = s;
		}
		
		@Override
		protected void confirmValue() {
			setValue(mCachedValue);
		}
		
		/*
		 * PhotoAccepted()
		 * Called when camera activity returns with a success code.
		 * mfk pass storedValue in? but need all the state anyway in checkAndProcessScore
		 */
		protected void PhotoAccepted() {
			// Note the value is not the full path, the difficulty is the difference
			// between the server path, or url, and local path. URL should perhaps be uploaded to server.
			// xxx But we need to get the local path from the saved value somehow.
			String storedValue = getTrial().getName() + "/" + PhotoFileBasename(); // Storing trial name is redundant?		
			mCachedValue = storedValue;
			checkAndProcessScore();
		}
		
		@Override
		protected void ClearValue() {
			super.ClearValue();
			File photoFile = new File(PhotoAbsolutePath());
			boolean deleted = photoFile.delete();
		}

		@Override
		protected void cleanup() {
			// TODO Auto-generated method stub
			
		}
	}
}
