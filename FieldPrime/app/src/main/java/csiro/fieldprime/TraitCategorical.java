package csiro.fieldprime;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import csiro.fieldprime.R;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import static csiro.fieldprime.DbNames.*;
import static csiro.fieldprime.Trait.Datatype.*;

public class TraitCategorical extends TraitInteger { // extension just to get WriteDatumJSON
	// INSTANCE DATA: ==========================================================================================
	private ArrayList<Category> mCategories = new ArrayList<Category>();
	
	// METHODS: ================================================================================================

	@Override
	Datum CreateDatum(TraitInstance ti, Node tu) {
		return new DatumCategorical(ti, tu);
	}

	@Override
	public Datatype getType() {
		return T_CATEGORICAL;
	}

	/*
	 * InitTraitJSON()
	 * Get the categories and their images. Note images have to be downloaded from the server.
	 * No support for update currently. We could just wipe the existing traitCategory entries
	 * and reload, but this might be a long operation (due to photos downloads), and we don't
	 * necessarily want to do this unnecessarily when - for most updates - the categories are
	 * unlikely to have changed.
	 */
	@Override
	Result initTraitJSON(JSONObject jtrt, Trial trl, boolean update) throws JSONException {
		if (update)
			return new Result(); // update not supported.

		// category image should give full file location, check case when this read from db
		JSONArray jcats = jtrt.getJSONArray("categories");
		for (int j = 0; j < jcats.length(); j++) {
			JSONObject jcat = jcats.getJSONObject(j);
			String caption = jcat.getString(TC_CAPTION);
			int value = jcat.getInt(TC_VALUE);
			String imageURL = jcat.isNull(TC_IMAGE_URL) ? null : jcat.getString(TC_IMAGE_URL);
			String imgFilePath = null;
			// Download the image file (if any) and store it locally:
			if (imageURL != null) {
				// Generate local file name to hold file, and create dirs if necessary:

				/*
				 * Image file storage location:
				 * NB: Here is the design choice about where to store files locally.
				 * The location/name is stored in the db, so knowledge of the choice mechanism should
				 * not be required elsewhere, and the name can be arbitrary, except it needs to be unique.
				 * Currently storage path/name for the image file is:
				 * <AppRootDir>/categoryImage/<TraitID>/<indexNum>.jpg
				 * Where indexNum is simply the position the category was in the JSON list.
				 * 
				 * NB cAppRootDir should represent a dir specific for current db.
				 * MFK - but then it would be DatabaseRootDir, and currently it isn't. Note
				 * that the storage dir is not even trial specific. Note however that the TraitID
				 * should be unique within a database. But not between databases, so if we want to
				 * support multiple databases some work is required (basically each database would need
				 * its own storage dir). Also note that since every trait is now local, having the same
				 * trait in multiple trials will require redundant storage of the same images.
				 */
				imgFilePath = Globals.g.getAppRootDir() + "categoryImages/" + getId() + "/" + j;//+ ".jpg";
				File imgFile = new File(imgFilePath);
				imgFile.getParentFile().mkdirs();

				// Get the file from the URL, and store it in the local file:
				Result res = Server.httpGetFile(imageURL, imgFilePath);
				if (res.bad()) 
					return res;  // MFK we are leaving things in an incomplete state here..
			}
			addCategory(value, caption, imgFilePath);
		}
		sortCategories();
		return new Result();
	}

	/*
	 * Categories()
	 * Returns ArrayList of Categories.
	 */
	public ArrayList<Category> Categories() {
		return mCategories;
	}

	/*
	 * int2StringRepresentation()
	 * Returns caption for category with the given value.
	 */
	@Override
	String int2StringRepresentation(int value) {
		for (Category cat : mCategories) {
			if (cat.value == value)
				return cat.caption;
		}
		return "Unexpectedly not found";
	}

	/*
	 * addCategory()
	 * Creates new category for this trait in the database, and adds it to the list.
	 * Note this trait must already have been inserted into the DB (as the id is a foreign key for TABLE_TRAIT_CATEGORY).
	 */
	private int addCategory(int value, String caption, String imageURL) {
		mCategories.add(new Category(value, caption, imageURL));
		ContentValues values = new ContentValues();
		values.clear();
		values.put(TC_TRAIT_ID, getId());
		values.put(TC_CAPTION, caption);
		values.put(TC_VALUE, value);
		values.put(TC_IMAGE_URL, imageURL);
		if (g_db().insert(TABLE_TRAIT_CATEGORY, null, values) < 0)
			return -1;
		return 0;
	}

	/*
	 * sortCategories()
	 * Sort category list by value. This will affect presentation order.
	 * 
	 */
	public class categoryComparator implements Comparator<Category>
	{
	    public int compare(Category left, Category right) {
	        return right.value - left.value;
	    }
	}
	private void sortCategories() {
		Collections.sort(mCategories, new categoryComparator());
	}

	
	@Override
	void SetFromDB(Trial trl) {
		String qry = String.format(Locale.US, "select %s,%s,%s from %s where %s = %d", TC_VALUE, TC_CAPTION, TC_IMAGE_URL, TABLE_TRAIT_CATEGORY,
				TC_TRAIT_ID, getId());
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(qry, null);
			if (ccr.moveToFirst())
				do {
					String imageURL = (ccr.isNull(ccr.getColumnIndex(TC_IMAGE_URL))) ? null :
						ccr.getString(ccr.getColumnIndex(TC_IMAGE_URL));
					mCategories.add(new Category(ccr.getInt(ccr.getColumnIndex(TC_VALUE)),
							ccr.getString(ccr.getColumnIndex(TC_CAPTION)), imageURL));
				} while (ccr.moveToNext());
		} finally { if (ccr != null) ccr.close(); }
		sortCategories();
	}

	@Override
	public String repSetStats(RepSet rs) {
		return HistogramRepSetStats(rs);
	}
	
	// SUB CLASSES: ============================================================================================

	public class Category {
		String caption;
		int value;
		String imageFile;
		
		Category(int value, String caption, String imageURL) {
			this.caption = caption;
			this.value = value;
			this.imageFile = imageURL;
		}
	}

	public class DatumCategorical extends DatumIntegerBase {
    	ScoreText mMyScoreText;
		
		public DatumCategorical(Trial.TraitInstance ti, Node tu) {
			super(ti, tu);
		}

		/*
		 * GetValidValueAsString()
		 * For screen presentation the value may be different to the integer value.
		 */
		@Override
		protected String GetValidValueAsString() {
			return ((TraitCategorical) GetTrait()).int2StringRepresentation(mValue);
		}

		@Override
		protected String GetValidValueAsStringForDB() {
			return Integer.toString(mValue);
		}

		@Override
		protected View scoreView() {
			LinearLayout xlayout = new LinearLayout(mCtx); // need enclosing layout for some reason, it seems otherwise only the first addition to vg is shown
			xlayout.setOrientation(LinearLayout.VERTICAL);

			mMyScoreText = new ScoreText(null, InputType.TYPE_CLASS_TEXT, false, this, mCtx, "Options:");
			mMyScoreText.setInputType(InputType.TYPE_NULL);
			mMyScoreText.setEnabled(false);
			xlayout.addView(mMyScoreText);

			HorizontalScrollView hsv = new HorizontalScrollView(mCtx);
			hsv.setScrollbarFadingEnabled(false);
			LinearLayout ilayout = new LinearLayout(mCtx);
			//LayoutParams hsvParams = ilayout.getLayoutParams();
			//hsvParams.height = 300;
			ilayout.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout.LayoutParams buttParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1);
			buttParams.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));
			
			for (TraitCategorical.Category cat : ((TraitCategorical) GetTrait()).Categories()) {
				if (cat.imageFile != null) {
					// Get image as bitmap
					Bitmap alan = BitmapFactory.decodeFile(cat.imageFile);
					ImageView img = new ImageView(mCtx);
					img.setImageBitmap(alan);
					img.setId(cat.value);
					img.setOnClickListener(new View.OnClickListener() {
						public void onClick(View v) {
							saveValue(v.getId());
						}
					});
					ilayout.addView(img, buttParams);
				} else {
					Button b = new Button(mCtx);
					b.setText(cat.caption);
					b.setBackgroundResource(R.color.white);
					b.setWidth(Util.dp2px(150)); // MFK Find screen size!
					b.setHeight(Util.dp2px(150)); // MFK Find screen size!
					b.setId(cat.value);
					b.setOnClickListener(new View.OnClickListener() {
						public void onClick(View v) {
							int val = ((Button) v).getId();
							saveValue(val);  // MFK should do validation first, as is traitInteger buttons?
						}
					});
					ilayout.addView(b, buttParams);
				}
			}
			hsv.addView(ilayout);
			xlayout.addView(hsv);
			return xlayout;
		}
		
		@Override
		protected void drawSavedScore() {
			mMyScoreText.drawSavedScore();
		}

		@Override
		protected void cleanup() {
			mMyScoreText.cleanup();
		}
	}

}
