/*
 * Datum
 * The absence of a datum for a traitInstance means it has not been scored.
 * The presence of one with a null value indicates NA has been indicated.
 * 
 * 
 * 
 */


package csiro.fieldprime;

import static csiro.fieldprime.DbNames.DM_GPS_LAT;
import static csiro.fieldprime.DbNames.DM_GPS_LONG;
import static csiro.fieldprime.DbNames.DM_ID;
import static csiro.fieldprime.DbNames.DM_NODE_ID;
import static csiro.fieldprime.DbNames.DM_SAVED;
import static csiro.fieldprime.DbNames.DM_TIMESTAMP;
import static csiro.fieldprime.DbNames.DM_TRAITINSTANCE_ID;
import static csiro.fieldprime.DbNames.DM_USERID;
import static csiro.fieldprime.DbNames.DM_VALUE;
import static csiro.fieldprime.DbNames.TABLE_DATUM;

import java.sql.Date;
import java.text.SimpleDateFormat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import csiro.fieldprime.Trait.Datatype;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

public abstract class Datum implements Trial.IDatum {
	// CONSTANTS: ========================================================================================================
	private final static int NA_BUTTON = 1;
	private final static int HELP_BUTTON = 2;
	private final static int CLEAR_BUTTON = 3;
	private final static int DYNAMIC_ADD_BUTTON = 4;
	private final static int DYNAMIC_PREV_BUTTON = 5;
	private final static int DYNAMIC_NEXT_BUTTON = 6;
	
	// STATIC DATA: ====================================================================================================
	
	// INSTANCE DATA: ===============================================================================================
	private long mId;     // Database id, or -1 if no db record.
	protected long mNodeId;
	protected Node mNode;
	protected Trial.TraitInstance mTraitInst;
	private boolean mIsNA = false;
	private FrameLayout mDisplayFrame;
	
	private boolean mDirty;   // not used yet, maybe should be on traitInstance rather than datum
	public boolean isDirty() {
		return mDirty;
	}
	public void setDirty(boolean dirty) {
		mDirty = dirty;
		if (dirty)
			mTraitInst.setDirtyBit(dirty); // cascading to ti doesn't really work, setting dirty is OK, but setting clean no..
	}

	// These 3 may not be necessary if this info lives just in the db.
	private long mTimestamp;
	private double mGpsLong;
	private double mGpsLat;
	private Location mLocation;
	private String mUserId;
	private int mSaved = 0;
	
	private boolean mNavigatingAway = false;  // need clear function?
	    // Used to avoid problem where if we navigate away from
        // a node - eg by the Next button - we end up calling autoProgress when the score is entered and thus
	    // moving on an extra node (if auto progress is configured). This should be set to true by sub classes
	    // that implement navigating away if they going to call scoreEntered.
	
	// Display stuff:
	protected Context mCtx;
	
	public interface CallOut {
		public void AutoProgress();
		public void dynSetTI(TraitInstance ti);
	}
	private CallOut mCallOut;
	
	// STATIC METHODS: ===============================================================================================

	/*
	 * Init()
	 * Stores database stuff needed for much of the other functionality.
	 * This should be called BEFORE anything else.
	 */
//	static public void Init() {
//		g = Globals.g;
//	}

	// ABSTRACT METHODS: ===============================================================================================
	
	/*
	 * WriteValue()
	 * Write the current datum value into values (type specific) with the given key.
	 * This is for the purpose of storing the value into a database.
	 * Note, the class doesn't need to know anything about what table or field name
	 * is being used. But it does need to know what type to write in.
	 */
	protected abstract void WriteValue(String valKey, ContentValues values);

	/*
	 * SetValueFromDB()
	 * Set the datum value from the database record crs, in which the value
	 * has key valKey.
	 */
	protected abstract void SetValueFromDB(String valKey, Cursor crs);

	/*
	 * GetValidValueAsString()
	 * Return the value as a string. Note this should only be called when the datum has a value, which
	 * is not NA. The type specific implementation may therefore skip any checks, and simply return a
	 * string representation of the value.
	 */
	protected abstract String GetValidValueAsString();

	/*
	 * scoreView()
	 * Returns a view containing a scoring interface for the type.
	 */
	protected abstract View scoreView();
	
	/*
	 * confirmValue()
	 * Set value from internal cached value.
	 * The intention is that the datum via trait specific code allows
	 * user to enter value. The trait specific datum code caches this
	 * value and then calls checkProcessScore for non trait specific checks,
	 * if these pass then ConfirmValue is called to tell the trait specific code
	 * that the value (which it is assumed to have cached) is accepted and
	 * written to DB, so if the trait specific code needs to do something to
	 * reflect this it can. This complication seems unavoidable since the 
	 * non trait specific checks may need user interaction and so must be
	 * asynchronous.
	 */
	protected abstract void confirmValue();
	
	
	/*
	 * drawSavedScore()
	 */
	protected abstract void drawSavedScore();
	
	
	/*
	 * cleanup()
	 * Displayed datum is going to be removed from display. Do any cleanup
	 * necessary. In particular, remove the soft keyboard if it's up.
	 */
	protected abstract void cleanup();
	
	// INSTANCE METHODS: ===============================================================================================

	// Constructor:
	public Datum(Trial.TraitInstance ti, Node tu) {
		mTraitInst = ti;
		mNode = tu;
		mNodeId = tu.getId();   // need to store id separately?
		mId = -1;
	}
	
	public long getId() {
		return mId;
	}

	/*
	 * reset()
	 * Clear non db state (used to make a cached datum as if it were retrieved from db).
	 */
	public void reset() {
		mNavigatingAway = false;
	}
	
	public boolean isSaved() {
		return !(mSaved == 0);
	}
	public void setSaved(boolean saved) {
		mSaved = saved ? 1 : 0;
	}
	
	public TraitInstance getTraitInstance() {
		return mTraitInst;
	}
	public Node getNode() {
		return mNode;
	}
	public long getTimestamp() {
		return mTimestamp;
	}

	public String getTimestampString() {
		Date date = new Date(mTimestamp);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		return formatter.format(date);
	}

	public double getGpsLongitude() {
		return mGpsLong;
	}

	public double getGpsLatitude() {
		return mGpsLat;
	}

	public String getUserId() {
		return mUserId;
	}

	/*
	 * checkAndProcessScore()
	 * When a score has been entered (captured by trait type specific
	 * code), this should be called to do processing that is independent
	 * of the trait type. Currently this involves getting and checking
	 * the GPS location. And getting confirm for overwrite.
	 * This function may need to interact with user and so is asynchronous.
	 * Hence the chained structure.
	 * Parameter confirm indicates whether we need to call back to the
	 * abstract ConfirmValue() - this needed since this function can be
	 * called from this (base class) to set the value to NA, as well as
	 * from the child classes to set type specific values.
	 */
	private void checkAndProcessScore(boolean confirm) {
		checkProcessScore_2(confirm);
	}
	protected void checkAndProcessScore() {
		checkAndProcessScore(true);
	}
	private void checkProcessScore_0(boolean confirm) {
		if (confirm)
			confirmValue();   // Confirm value is approved
		Util.toast("CHECK PROCESS SCORE 0");
		DBWrite();        // Save current val to db (trait specific val determination)
		drawSavedScore(); // Display saved val (may be trait specific)
		ScoreEntered();   // Inform creator that score entered
	}	
	// Overwrite check:
	private void checkProcessScore_1(final boolean confirm) {
		final TraitInstance fti = this.getTraitInstance();
		if (hasDBValue())
			Util.Confirm("Confirm Overwrite", "Overwrite the existing value (and timestamp and location)?", 0, new Util.ConfirmHandler() {
				@Override
				public void onYes(long context) {
					checkProcessScore_0(confirm);
				}
				@Override
				public void onNo(long context) {
					fti.clearCachedDatum(); // score not saved, so cached datum may not reflect db
				}
			});
		else
			checkProcessScore_0(confirm);
	}
	// GPS check
	private void checkProcessScore_2(final boolean confirm) {
//		Location tuLoc = new Location("");
//		tuLoc.setLatitude(-35.2638);
//		tuLoc.setLongitude(149.1181);
		if (!mNode.hasLocation()) { // nothing to check if tu has no location.
			checkProcessScore_1(confirm);
			return;
		}
		int maxDelta = Globals.g.prefs().GetMaxGPSDelta();
		if (maxDelta > 0) {
			mLocation = GPSListener.getLocation();  // get current location
			if (mLocation != null) {
				Location tuLoc = mNode.getLocation();
				float d = tuLoc.distanceTo(mLocation);
				if (d > maxDelta) {
					Util.Confirm("GPS Location Alert",
							"You appear to be " + d + " metres away from the node, save the score anyway?", 0,
							new Util.ConfirmHandler() {
								@Override
								public void onNo(long context) {
								}

								@Override
								public void onYes(long context) {
									checkProcessScore_1(confirm);
								}
							});
					return;
				}
			}
		}
		checkProcessScore_1(confirm); 
	}

	/*
	 * hasDBValue()
	 * Indicates whether this datum has a value in the db, currently using mId to know this.
	 */
	@Override
	public boolean hasDBValue() {
		return mId != -1;
	}

	/*
	 * DeleteRecord()
	 * Deletes ALL Datum database records for this datum's node and traitInstance. 
	 * NB, attow there can only be one datum for a node/ti, but this could potentially change.
	 * In that case we might want to be able to delete a single datum, which we probably
	 * would have to identify by id.
	 */
	private int DeleteRecord() {
		mTraitInst.clearCachedDatum();
		String deleteClause = String.format("%s = %d and %s = %d",
				DM_NODE_ID, mNodeId, DM_TRAITINSTANCE_ID, mTraitInst.getId());
		int numGone = Globals.g.db().delete(TABLE_DATUM, deleteClause, null);
		mId = -1;
		return numGone;
	}
	

	/*
	 * ClearValue()
	 * Clear score from database (removes records).
	 * NB, if a trait stores any data additional to the datum record, then its datum class
	 * should override this.
	 */
	protected void ClearValue() {
		if (1 != DeleteRecord()) {
			Util.msg("Warning, delete may not have worked");
		}
		SetNA(false);
	}
	
	// NA status:
	public boolean isNA() {
		return mIsNA;
	}
	public void SetNA(boolean isNA) {
		mIsNA = isNA;
	}

	public Datatype GetType() {
		return mTraitInst.datatype();
	}

	public Trait GetTrait() {
		return mTraitInst.getTrait();
	}
	
	public Trial getTrial() {
		return mTraitInst.getTrial();
	}

	/*
	 * DBWrite()
	 * Writes to database. This could be an insert OR update, but for the moment we are having only
	 * inserts, each with a timestamp. NB we assume various things set in this.
	 * NB - assumes there is something to write, and it may just be a comment. MFK investigate.
	 */
	protected void DBWrite() {
		mTraitInst.setDirtyBit(true); // not used 
		
		/*
		 * Since we are writing to the DB, we must clear the cached Datum in this TraitInstance.
		 * We don't know the the cached Datum is in fact this one. But it may be, so we clear it.
		 */
		mTraitInst.clearCachedDatum();
		
		ContentValues values = new ContentValues();
		values.put(DM_TRAITINSTANCE_ID, mTraitInst.getId());
		values.put(DM_NODE_ID, mNodeId);

		// Write (and record) current userid, timestamp, longitude and latitude:
		values.put(DM_USERID, mUserId = Globals.getUsername());
		values.put(DM_TIMESTAMP, mTimestamp = System.currentTimeMillis());
		values.put(DM_GPS_LONG, mGpsLong = GPSListener.getLongitude());
		values.put(DM_GPS_LAT, mGpsLat = GPSListener.getLatitude());
		values.put(DM_SAVED, mSaved = 0);
	
		if (isNA()) {
			values.putNull(DM_VALUE); // write null value (i.e. explicit NA)
		} else {
			WriteValue(DM_VALUE, values);
		}
		
		//long oldId = mId;
		/*
		 * Why replace here? For overwriting, if so what does it replace?
		 * Note that a genuine replace can change the db _id field, it seems
		 * to be implemented as a delete then insert. It might be better
		 * to do an update (once we know that it's not a new insert).
		 */
		mId = Globals.g.db().replace(TABLE_DATUM, null, values);
		if (mId < 0) {
			Util.msg("Failed creation of new datum db record");
		}
		else {
			setDirty(false);  // since we have just written to the db, the datum is clean.
		}
		//long x = Trait.numDatums();
		//Util.toast("numDat:" + x + " newId:" + mId + " oldId:" + oldId);
	}

	/*
	 * ValueAsString()
	 * Return string representation of value, for screen presentation.
	 */
	public String ValueAsString(String absentVal) {
		if (!hasDBValue())
			return absentVal;
		if (isNA())
			return "NA";
		return GetValidValueAsString();
	}

	/*
	 * ValueAsStringForDB()
	 * Return string representation of value, for database storage (attow just for outputting csv).
	 * Will be the same as ValueAsString above, unless GetValidValueAsStringForDB() overridden by the subclass.
	 */
	protected String GetValidValueAsStringForDB() {
		return GetValidValueAsString();
	}

	public String ValueAsStringForDB() {
		if (!hasDBValue())
			return "";
		if (isNA())
			return "NA";
		return GetValidValueAsStringForDB();
	}

	/*
	 * getFromDB()
	 * Return Datum, either as read from the db, or a new one if it wasn't present in the db.
	 * Assumes that traitInstance_id, type, and node_id are set.
	 * Returns Datum in a Result.
	 */
	public static Result getFromDB(Trial.TraitInstance ti, Node tu) {
		Datum dat = getFromDBIfPresent(ti, tu);
		if (dat == null) {
			dat = ti.getTrait().CreateDatum(ti, tu);
		}
		return new Result(dat);
	}
	
	/*
	 * getFromDBIfPresent()
	 * Assumes that traitInstance_id, type, and node_id are set.
	 * If there is no matching Datum in DB, then nothing is changed.
	 * Returns Datum in a Result.
	 * Note if retrieval time becomes problematic, it would help to
	 * have an index on ti/node.
	 */
	public static Datum getFromDBIfPresent(Trial.TraitInstance ti, Node tu) {
		Datum dat = ti.getTrait().CreateDatum(ti, tu);

		String whereCond = String.format("where d.%s=%d and d.%s=%d",
				DM_TRAITINSTANCE_ID, ti.getId(), DM_NODE_ID, tu.getId());
		// MFK - I think this complication below (inner join) may now be unnecessary
		// as we only allow one datum per ti/node. It was intended, I think, to get
		// only the most recent datum for a ti/node pair.
//		String innerJoin = String.format(
//				"inner join(select %s, %s, max(%s) timestamp from %s group by %s, %s) ss on d.%s = ss.%s and d.%s = ss.%s ",
//				DM_TRAITINSTANCE_ID, DM_NODE_ID, DM_TIMESTAMP, TABLE_DATUM, DM_TRAITINSTANCE_ID, DM_NODE_ID,
//				DM_TRAITINSTANCE_ID, DM_TRAITINSTANCE_ID, DM_TIMESTAMP, DM_TIMESTAMP);
		String qry = String.format("select d.%s, d.%s, d.%s, d.%s, d.%s, d.%s, d.%s from %s d "
				/*+ innerJoin */ + whereCond,
				DM_ID, DM_TIMESTAMP, DM_USERID, DM_GPS_LONG, DM_GPS_LAT, DM_VALUE, DM_SAVED, TABLE_DATUM);
		Cursor ccr = null;
		try {
			ccr = Globals.g.db().rawQuery(qry, null);
			int numExists = ccr.getCount();
			if (numExists != 1) { // if no entry yet in db return new Datum
				return null;
			}
			ccr.moveToFirst();
	
			dat.mId = ccr.getInt(ccr.getColumnIndex(DM_ID));
			dat.mTimestamp = ccr.getLong(ccr.getColumnIndex(DM_TIMESTAMP));
			dat.mUserId = ccr.getString(ccr.getColumnIndex(DM_USERID));
			dat.mGpsLong = ccr.getDouble(ccr.getColumnIndex(DM_GPS_LONG));
			dat.mGpsLat = ccr.getDouble(ccr.getColumnIndex(DM_GPS_LAT));
			dat.mSaved = ccr.getInt(ccr.getColumnIndex(DM_SAVED));
			dat.mNode = tu;
			
			// Value field: Null implies NA:
			if (ccr.getType(ccr.getColumnIndex(DM_VALUE)) == Cursor.FIELD_TYPE_NULL)
				dat.SetNA(true);
			else
				dat.SetValueFromDB(DM_VALUE, ccr);
		} finally { if (ccr != null) ccr.close(); }
		return dat;
	}
	
	// #########################################################################################################
	// Display Stuff ###########################################################################################
	// #########################################################################################################
	// Lots of UI dependencies here. It might be nice to have this in a separate file somehow, for portability
	// to different UIs.

	// Find out if has score and/or notes, perhaps should be greyed if neither.
	// Ideally would warn if had been uploaded to server.

	// DATA-INSTANCE: ====================================================================================================
	public void Display(Context ctx, FrameLayout frame, CallOut scoreEntered) {
		mCallOut = scoreEntered;
		mCtx = ctx;
		final Datum fDat = this;
		mDisplayFrame = frame;
		mDisplayFrame.removeAllViews(); // Clear the frame before adding to it. 

		// Make vertical linear layout to hold the view scoring pane, and the button bar below it:
		LinearLayout scoreAndBB = new LinearLayout(mCtx);
		scoreAndBB.setOrientation(LinearLayout.VERTICAL);
		scoreAndBB.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		// Make the button bar that is at bottom of screen for all types:
		//ButtonBar bottomBB = new ButtonBar(mCtx, scoreAndBB, new View.OnClickListener() {
		ButtonBar bottomBB = new ButtonBar(mCtx, new View.OnClickListener() {
			public void onClick(View v) {
				switch (v.getId()) {
				case NA_BUTTON:
					fDat.SetNA(true);
					checkAndProcessScore(false);
					break;
				case HELP_BUTTON:
					Trait trt = this.getTraitInstance().getTrait();
					String desc = trt.mDescription;
					if (desc.length() == 0)
						desc = String.format("No Description has been provided for trait %s", trt.getCaption());
					Util.msg(desc);
					break;
				case CLEAR_BUTTON:
					if (!hasDBValue()) {  // Ideally don't need this, disable button when no value..
						Util.msg("Score is already clear");
						break;
					}
					Util.Confirm("Clear", "Do you really want to clear the score?\n"
							+ "Note this will not clear the score on the server, " 
							+ "if it has already been uploaded", 0,
							new Util.ConfirmHandler() {
								@Override
								public void onYes(long context) {
									ClearValue();
									drawSavedScore();
								}
								@Override public void onNo(long context) {}
							});
					break;
				case DYNAMIC_NEXT_BUTTON:
					TraitInstance nextTi = getTraitInstance().next();
					if (nextTi != null) {
						navigatingAwayWithWarning();
						mCallOut.dynSetTI(nextTi);  
					}
					else
						Util.toast("At last already"); // and  beep?
					break;
				case DYNAMIC_PREV_BUTTON: {
					TraitInstance prevTi = getTraitInstance().prev();
					if (prevTi != null) {
						navigatingAwayWithWarning();
						mCallOut.dynSetTI(prevTi);
					}
					else
						Util.toast("At first already"); // and  beep?
					break;
				}
				case DYNAMIC_ADD_BUTTON:
					RepSet r = fDat.getTraitInstance().getRepset();
					r.append();
					break;
				}
			}

			private TraitInstance getTraitInstance() {
				return mTraitInst;
			}
		});
		bottomBB.addButton("Set to NA", NA_BUTTON);
		if (getTraitInstance().getRepset().isDynamic()) {
			bottomBB.addButton("<", DYNAMIC_PREV_BUTTON);
			bottomBB.addButton(">", DYNAMIC_NEXT_BUTTON);
			bottomBB.addButton("+", DYNAMIC_ADD_BUTTON);
		}
		bottomBB.addButton("Clear", CLEAR_BUTTON);
		bottomBB.addImageButton("Help", HELP_BUTTON);
		
		View v = scoreView();
		v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1)); // weight to 1 to fill space
		scoreAndBB.addView(v);                   // add the scoreView		
		scoreAndBB.addView(bottomBB.Layout());   // add the button bar
		mDisplayFrame.addView(scoreAndBB);
	}
	
	/*
	 * Redisplay()
	 * Refills the display layout, should only be called after Display() has been called.
	 */
	protected void Redisplay() {
		Display(mCtx, mDisplayFrame, mCallOut);
	}


	/*
	 * ScoreEntered()
	 * Called by implementing class to indicated score has been entered.
	 */
	protected void ScoreEntered() {
		if (!mNavigatingAway)
			mCallOut.AutoProgress();
		else
			mNavigatingAway = false;   // may not be necessary
	}

	/*
	 * navigatingAway()
	 * Inform trait specific datum code that user has navigated away from this datum. The user can
	 * 'navigate away' from a datum by hitting prev/next buttons, or a different trait instance in the
	 * trait selector. Note there are other ways to navigate away (eg search and select another node,
	 * or close the trial), but these do not ATTOW invoke navigatingAway().
	 * This is used to write current value without user explicitly doing so. EG triggered when user skips
	 * directly to another score set, it may be desirable to write the score they had just entered on a keyboard.
	 * This version does nothing, but implementing class may override it if appropriate. Not abstract since for
	 * some data types it may not be relevant (eg when score is entered by clicking buttons rather than typing
	 * at a keyboard), so useful to supply a default version here.
	 * Returns boolean indicating whether datum is OK with that. I.e. if it had nothing
	 * to save, or it did and it saved it successfully. It might return false if for example
	 * it tried to save the current unsaved user entered value but there was an error in it.
	 */
	public boolean navigatingAway() {
		mNavigatingAway = true;
		return true;
	}
	
	/*
	 * navigatingAwayWithWarning()
	 * written because muliple calls to navigatingAway simply issued warning based on return value. DRY.
	 */
	public void navigatingAwayWithWarning() {
		cleanup();
		if (!navigatingAway()){
			Util.msg("Warning last entered value may not be saved");
		}
	}

	/*
	 * processTextValue()
	 * Set value of datum from string. This version does nothing, but subtypes may override this
	 * if appropriate. This may be used, to set a value received from a bluetooth device, for example.
	 * Returns boolean indicating if value was set.
	 * MFK - it might be better to have this return a Result, so caller can display error message.
	 */
	public boolean processTextValue(String val) {
		return false;
	}
	
	/*
	 * setStickyValue()
	 * Does nothing but may be overridden.
	 */
	public void setStickyValue() {
	}
}
