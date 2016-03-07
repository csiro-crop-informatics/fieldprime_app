package csiro.fieldprime;

import java.util.ArrayList;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

import static csiro.fieldprime.DbNames.*;

/*
 * There are various categories of traits. Firstly traits are either "system" or "local" (trial specific),
 * and these are marked by having the value SYSTYPE_SYSTEM or SYSTYPE_TRIAL respectively for the 
 * sysType field in both the trait data structure and database field.
 * System traits exist independently of any trial and cannot be created on an ad-hoc basis by a
 * user. These are intended to represent a set of standard traits, managed by an administrator.
 * A given system trait can be associated with any number of trials (zero or more). System traits
 * can only be downloaded from a server. Any traits created from a csv file are created as local
 * traits.
 * Local, or trial specific traits are associated with a particular single trial, and cannot be associated
 * with multiple trials.
 * Note that traits downloaded from a server can be either system or local.
 * 
 * NOTE. THIS IS NO LONGER TRUE, ALL TRAITS DOWNLOADED FROM SERVER ARE LOCAL.
 * 
 * Ad Hoc traits.
 * Another type of trait is the ad-hoc trait (not yet fully implemented) which is intended to be a user
 * created trait. These would be local traits, and if uploaded to the server, the trait definition would
 * need to be uploaded along with any data collected for the trait. 
 * Ad Hoc traits are identified by the mSysType field, and perhaps by serverId (of 0).
 * 
 * SERVER ISSUES (current thinking).
 * Do we need to be able to download trait instances (and data)?
 * If so, what if data is added to these. How do we merge the data for the
 * same trait instances present and modified on multiple devices?
 * We could, for example, allow the download of trait instance data, but make
 * it read only (it could be used for comparison or constraint purposes).
 * Alternatively, we could disallow trait instance downloads, which would be simpler.
 */
public abstract class Trait {
	// CONSTANTS: ====================================================================================================
	static final int DEFAULT_MIN = 0;  // MFK not here
	static final int DEFAULT_MAX = 100;
	static final int SYSTYPE_TRIAL = 0;
	static final int SYSTYPE_SYSTEM = 1;
	static final int SYSTYPE_ADHOC = 2;
	static final int SYSTYPE_ATTRIBUTE = 3;

	/*
	 * Enum of datatypes for traits/attributes.
	 */
	static public enum Datatype {
		/*
		 * NB Some code may assume that these values (except T_NONE) are indexes into TRAIT_TYPE_NAMES. 
		 * I.e. they should start at zero and be consecutive, code within this enum also assumes this.
		 */
		T_NONE(-1),
		T_INTEGER(0),
		T_DECIMAL(1),
		T_STRING(2),
		T_CATEGORICAL(3),
		T_DATE(4),
		T_PHOTO(5),
		T_LOCATION(6);
		
		private static final int MAXVAL = 6;
        private int mValue;
        private Datatype(int value) {
                mValue = value;
        }

		/*
                 * value()
                 * Convert Datatype to int.
                 */
        public int value() { return mValue; }
        
        /*
         * isNumeric()
         * Return boolean indicating if a (single valued) numeric type.
         * MFK should we have function to return db storage type?
         */
        public boolean isNumeric() {
        	switch (this) {
        	// list all (single value) numeric types here:
			case T_CATEGORICAL:
			case T_DATE:
			case T_DECIMAL:
			case T_INTEGER:
				return true;
			default: return false;
        	}
        }
        
        /*
         * fromInt()
         * Convert int to Datatype.
         */
        static public Datatype fromInt(int val) {
        	if (val == -1) return T_NONE;
        	if (val < -1 || val > MAXVAL) return null;
        	return values()[val+1];
        }
        
        /*
         * compare()
         * Compare function for 2 values (as objects).
         */
        public int compare(Object aval, Object bval) {
        	// deal with null cases first, make null greater than non-null.
        	// MFK - do we need to deal with NAs?
        	if (aval == null) {
        		return bval == null ? 0 : 1;
        	}
        	if (bval == null) return -1;
        	
        	switch (this) {
			case T_DECIMAL:
				return ((Double)aval).compareTo((Double)bval);
			case T_CATEGORICAL:
			case T_DATE:
			case T_INTEGER:
				return ((Integer)aval).compareTo((Integer)bval);
			case T_STRING:
				return ((String)aval).compareTo((String)bval);
			case T_LOCATION:
			case T_NONE:
			case T_PHOTO:
			default:
				return 0;
        	}
        }
	}
	
	static final String DT_INTEGER = "Integer";
	static final String DT_DECIMAL = "Decimal";
	static final String DT_STRING = "String";
	static final String DT_CATEGORICAL = "Categorical";
	static final String DT_DATE = "Date";
	static final String DT_LOCATION = "Location";
	//static final String[] TRAIT_TYPE_NAMES = { DT_INTEGER, DT_DECIMAL, DT_STRING, DT_CATEGORICAL };
	
	// STATIC DATA: ==================================================================================================

	// List of sys traits. Currently unsorted array list, but private, so can switch to sorted, or use a map later if necessary..
	static private ArrayList<Trait> mSysTraitList = new ArrayList<Trait>(); // The list of system traits

	// INSTANCE DATA: ================================================================================================
	private long mId = -1;
	private int mSysType = SYSTYPE_TRIAL; // default is local trait
	private int mServerId = 0;
	private String mUploadURL;
	private String mCaption = "";
	public String mDescription = "";
	private int mBarcodeAttId = -1;
	
	/*
	 * Sticky stuff:
	 * If a trait is specified as "sticky", then when it is being scored the trait will
	 * automatically be set to the last value that was set. This intended to speed things
	 * up where a trait tends to be the same value for multiple nodes that are scored
	 * sequentially. The use case for which is was invented was using FieldPrime to record
	 * data about collections of seed packets. These were stored in boxes with many packets
	 * in each box. The box id was one of the datums collected for each packet and so the box id
	 * would remain the same for each packet (where each packet was a node).
	 */
	private boolean mSticky = false;
	void setSticky(boolean sticky) {
		mSticky = sticky;
	}
	boolean isSticky() {
		return mSticky;
	}
	
	/*
	 * Flags
	 */
	static final private int FLAG_SINGLE_INSTANCE = 1;
	private int mFlags = 0;
	public boolean isSingleInstance() {
		return (mFlags & FLAG_SINGLE_INSTANCE) == FLAG_SINGLE_INSTANCE;
	}
	public void setSingleInstance(boolean singleInstance) {
		if (singleInstance)
			mFlags |= FLAG_SINGLE_INSTANCE;
		else
			mFlags ^= FLAG_SINGLE_INSTANCE;
	}
	
	// ABSTRACT FUNCTIONS: ===========================================================================================

	/*
	 * getType()
	 */
	abstract public Datatype getType(); 
	
	/*
	 * CreateDatum()
	 * Create a new Datum (with sub type matching the trait's).
	 */
	abstract Datum CreateDatum(Trial.TraitInstance ti, Node tu);

	/*
	 * WriteDatumJSON()
	 * Write appropriate JSON into the jdatum object to represent a datum's value,
	 * where the datum in question is represented by the current line in the csr,
	 * which holds the value field from the database. The JSON if for sending to the server,
	 * so the representation written should be in the form expected by the server.
	 */
	abstract boolean WriteDatumJSON(JSONObject jdatum, Cursor csr) throws JSONException;
	
	/*
	 * initTraitJSON()
	 * Type specific handling of JSON trait definition.
	 * Update indicates whether this is being called for a new trait, or if this
	 * is an update.
	 * NB, this could be virtual rather than abstract, since not every trait needs it.
	 * NB, the trait should probably exist in the db prior to calling this.
	 */
	abstract Result initTraitJSON(JSONObject jtrt, Trial trl, boolean update) throws JSONException;

	/*
	 * repSetStats()
	 * Return report of statistics relevant to the trait type
	 */
	abstract public String repSetStats(RepSet repSet);
	
	/*
	 * Validate()
	 * 
	 */
	//abstract public String Validate();
	
	/*
	 * SetFromDB()
	 * Called after loading trait data from the database. Should be overridden by any trait type that needs
	 * to do more that just get the trait table fields.
	 * Note some traits may need to know the trial that the trait is being used for.
	 * However (MFK) since we no longer have sys traits, this could be implicit. If it becomes to,
	 * i.e. with a trial_id attribute in Trait, then the Trial param would be unnecessary.
	 */
	abstract void SetFromDB(Trial trl);

	abstract boolean SupportsBluetoothScoring();
	
	// FUNCTIONS: ====================================================================================================


	@Override
	public String toString() {
		return this.getCaption();
	}

	static protected SQLiteDatabase g_db() {
		return Globals.g.db();
	}

	/*
	 * setServerId()
	 * Save the server id, and write updated value to database.
	 */
	public boolean setServerId(int serverId) {
		ContentValues values = new ContentValues();
		values.put(TR_SERVER_ID, serverId);
		int numUpdated = g_db().update(TABLE_TRAIT, values, String.format("%s = %d", TR_ID, getId()), null);
		if (numUpdated == 1) {
			mServerId = serverId;
			return true;
		}
		return false;
	}
	private int getServerId() {
		return mServerId;
	}


	public void setId(long id) {
		this.mId = id;
	}

	public long getId() {
		return mId;
	}
	
	public String getUploadURL() {
		return mUploadURL;
	}
	
	public String getCaption() {
		return mCaption;
	}
	public String getDescription() {
		return mDescription;
	}

	public boolean isSystemTrait() {
		return mSysType == SYSTYPE_SYSTEM;
	}

	public boolean isAdHocTrait() {
		return mSysType == SYSTYPE_ADHOC || mServerId == 0;
	}
	public boolean hasServerID() {
		return mServerId > 0;
	}

	/*
	 * DeleteTraitInstance()
	 * Note sub classes should override this is they next to do extra stuff for delete.
	 * MFK Only used for all tis in repset.
	 */
	public Result DeleteTraitInstance(TraitInstance ti) {
		if (1 != g_db().delete(TABLE_TRAIT_INSTANCE, String.format("%s = %d", TI_ID, ti.getId()), null)) {
			return new Result(false, "Warning, delete from traitInstance table may not have worked");
		}
		return new Result();
	}

	
	/*
	 * makeContentValues()
	 * Return ContentValues object with all the trait db fields, ready for insert or replace operation.
	 */
	private ContentValues makeContentValues() {
		ContentValues values = new ContentValues();
		values.put(TR_ID, getId());
		values.put(TR_CAPTION, mCaption);
		values.put(TR_DESCRIPTION, mDescription);
		values.put(TR_TYPE, getType().value());
		values.put(TR_SYSTYPE, mSysType);
		values.put(TR_SERVER_ID, mServerId);
		values.put(TR_UPLOAD_URL, mUploadURL);
		if (mBarcodeAttId > 0)
			values.put(TR_BARCODE, mBarcodeAttId);
		else
			values.putNull(TR_BARCODE);
		return values;
	}
	
	/*
	 * InsertDB()
	 * 2 versions: One for trial specific traits (which require an entry in table trialTrait)
	 * and one for system traits (which don't).
	 * MFK 2 versions for historical reasons.
	 * ATTOW, this is only called as part of adding a trait to a trial (so it could be a single function).
	 * However it is possible that we would want to load a set of system traits independently of any trial.
	 */
	private Result InsertDB() {
		ContentValues values = makeContentValues();

		// Check constraints:
		Cursor ccr = null;
		try {
			String qry = String.format(Locale.US, "select %s, %s from %s where %s = %d", TR_ID, TR_SYSTYPE, TABLE_TRAIT, TR_ID, getId());
			ccr = g_db().rawQuery(qry,null);
			if (ccr.moveToFirst()) {
				return new Result(false, "trait id already exists");
			}
		} finally { if (ccr != null) ccr.close(); }
		try {
			long test = g_db().insertOrThrow(TABLE_TRAIT, null, values);
			if (test < 0)
				new Result(false, "Failed DB insertion of trait " + getCaption() + " unexpected error");
		} catch (android.database.SQLException e) {
			return new Result(false, "Failed DB insertion of trait " + getCaption() + ": " + e.getMessage());
		}

		return new Result();
	}

	// Create new db record for trait and associate it with specified dataset:
	private Result InsertDB(Trial trl) {
		// Create record in trait table:
		Result res = InsertDB();
		if (res.bad())
			return res;

		return AddToTrial(trl);
	}

	/*
	 * AddToTrial()
	 * Add this trait to the trialTrait table (for specified trial).
	 */
	public Result AddToTrial(Trial trl) {
		// Also need to create record in trialTrait table:
		ContentValues values = new ContentValues();
		values.put(TT_TRIALID, trl.getId());
		values.put(TT_TRAITID, getId());
		if (g_db().insert(TABLE_TRIALTRAIT, null, values) < 0)
			return new Result(false, "Failed DB insertion into trialTrail for trait " + getCaption());
		return new Result();		
	}
	
	/*
	 * UploadTraitInstance()
	 * This can be overridden by a trait type if required.
	 * NB, it is expected that this is called from an asynchronous thread (not the UI thread).
	 */
	public Result uploadTraitInstance(TraitInstance ti) throws JSONException {
		JSONObject json = traitInstanceDataAsJSON(ti);
		return Server.uploadJSON(json, getUploadURL());
	}
	

	// Static Stuff: ============================================================================================
	// All static

	/*
	 * GetSysTrait()
	 * Retrieves the specified system trait, or null if not present.
	 */
	static public Trait GetSysTrait(int id) {
		for (Trait trt : mSysTraitList) {
			if (trt.getId() == id)
				return trt;
		}
		return null;
	}
	
	
	/*
	 * numDatums()
	 * Returns num elements in Datum table, for debug purposes.
	 */
	static public long numDatums() {
		return DatabaseUtils.queryNumEntries(g_db(), TABLE_DATUM, null);
	}
	
	/*
	 * addOrUpdateTraitFromJSON()
	 * Create or update trait with the specified attributes and insert it into the DB.
	 * 
	 * Returns a Result with good status and containing the new trait if new trait created.
	 * Returns a Result with good status and no object if trait already existed.
	 * Returns a Result with bad status and errmsg if bad things have happened.
	 * Note use of object for min and max, to allow null values.
	 * 
	 * MFK - I have changed things so that ALL traits coming from the server are now
	 * local traits, not system traits (i.e. the concept of system traits only applies
	 * on the server). This change is not fully reflected in the app code yet. 
	 * Note this func, attow, is only called on receiving trial definition from the server.
	 */
	public static Result addOrUpdateTraitFromJSON(Trial trial, JSONObject jtrt) throws JSONException {
		// Extract generic trait metadata:
		int serverId = jtrt.getInt(TR_ID);
		String uploadURL = jtrt.isNull(TR_UPLOAD_URL) ? null : jtrt.getString(TR_UPLOAD_URL);
		String caption = jtrt.getString(TR_CAPTION);
		String description = jtrt.getString(TR_DESCRIPTION);
		// MFK TR_TYPE deprecated 25/3/15 delete TR_TYPE branch when no old clients
		Datatype dt = Datatype.fromInt(jtrt.getInt(jtrt.has(TR_DATATYPE) ? TR_DATATYPE : TR_TYPE));
		int barcodeAttId = jtrt.has("barcodeAttId") ? jtrt.getInt("barcodeAttId") : -1;   // Trait barcode

		/*
		 * Check whether this trait is already in the trial, if so then treat this as an update.
		 * Note that we might have to do something if we want to allow traits to
		 * be updated, i.e. change caption, description, or sub type specific attributes.
		 * For the moment however, a trait is unchangeable once associated with a trial.
		 */
		for (Trait tr : trial.getTraitList()) {
			if (tr.getServerId() == serverId) {
				/*
				 * Trait already exists. So this is an update. 
				 * The updateable fields are:
				 * caption, description, uploadURL.
				 * Also any trait type specific handling is invoked.
				 */
				tr.mCaption = caption;
				tr.mDescription = description;
				tr.mUploadURL = uploadURL;
				tr.mBarcodeAttId = barcodeAttId;
				if (g_db().update(TABLE_TRAIT, tr.makeContentValues(), String.format("%s = %d", TR_ID, tr.getId()), null) != 1)
					return new Result(false, "Error in database replace");
				
				// Type specific processing and return:
				return tr.initTraitJSON(jtrt, trial, true);
			}
		}
		
		/*
		 *  New trait, not already in db:
		 */
		Result res = Trait.NewTrait(SYSTYPE_TRIAL, trial, serverId, caption, description, dt, uploadURL, barcodeAttId);
		if (res.bad()) {
			Trial.DeleteTrial(trial.getId());
			return res;
		}
		Trait trt = (Trait) res.obj();

		// Type specific JSON processing:
		res = trt.initTraitJSON(jtrt, trial, false);
		if (res.bad()) {
			Trial.DeleteTrial(trial.getId());
			return res;
		} 
		return new Result();
	}
	
	
	/*
	 * NewTrait()
	 * Create a new trait with the specified attributes and insert it into the DB.
	 * Parameter sys indicates whether this is a system trait. If it is, then the new
	 * trait is added to the system list.
	 * Returns a Result containing the new trait on success.
	 */
	public static Result NewTrait(int sysType, Trial trl, int id, String caption, String description,
			Datatype type, String uploadURL, int barcodeAttId) {
		boolean sys = sysType == SYSTYPE_SYSTEM;   // MFK - should never be SYSTYPE_SYSTEM now
		
		if (sys && GetSysTrait(id) != null) // If this trait already exists, do nothing (assume it is the same).
			return null;
	
		// Check no trait with this caption is already in this trial:
		String qry = String.format(Locale.US, "select count(*) from %s join %s on %s = %s where %s = %d and %s = '%s'",
				TABLE_TRIALTRAIT, TABLE_TRAIT, TT_TRAITID, TR_ID, TT_TRIALID, trl.getId(), TR_CAPTION, caption);
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(qry, null);
			ccr.moveToFirst();
			if (ccr.getInt(0) != 0)
				return new Result(false, "Duplicate caption for trait in trial");
		} finally { if (ccr != null) ccr.close(); }

		Trait t = makeTrait(type, caption, description, uploadURL, barcodeAttId);
		
		t.mSysType = sysType;
		switch (sysType) {
		case SYSTYPE_SYSTEM:
			t.mId = id;
			t.mServerId = id;
			mSysTraitList.add(t);	
			break;
		case SYSTYPE_TRIAL:
		case SYSTYPE_ADHOC:
			t.mId = Globals.g.getDatabase().GenerateNewID(TABLE_TRAIT, TR_ID);
			t.mServerId = id;
			break;
		default:
			return new Result(false, "Unknown systype for trait");
		}
		
		Result res = t.InsertDB(trl);
		if (res.bad())
			return res;
		return new Result(t);
	}

	static private Trait makeTrait(Datatype type, String caption, String description, String uploadURL, int barcodeAttId) {
		Trait t;
		switch (type) {
		case T_INTEGER:
			t = new TraitInteger();
			break;
		case T_DECIMAL:
			t = new TraitDecimal();
			break;
		case T_STRING:
			t = new TraitString();
			// MFK temp hack - make trait named Box_ID sticky:
			// Sticky property is not stored in db, and probably should be configured
			// on the app, rather than on the server. Maybe in Select Traits to Score dialog.
			if (caption.equals("Box_ID"))
				t.setSticky(true);
			break;
		case T_CATEGORICAL:
			t = new TraitCategorical();
			break;
		case T_DATE:
			t = new TraitDate();
			break;
		case T_PHOTO:
			t = new TraitPhoto();
			break;
		default:
			return null;
		}

		// Set up fields
		t.mCaption = caption;
		t.mDescription = description;
		t.mUploadURL = uploadURL;
		t.mBarcodeAttId = barcodeAttId;
		return t;
	}

//	/*
//	 * Init()
//	 * Gets the database handle needed for this and other functions here.
//	 * And retrieves and stores the set of system traits in the db.
//	 * Note this should be called before any of the other static functions are called.
//	 */
//	static public boolean Init(Database topDB) {
//		g = Globals.g;
//		mTopDB = topDB;
//		return true;
//	}

	/*
	 * getTrialTraitListFromDB()
	 * Get trait list from the db for the specified trial.
	 */
	static public ArrayList<Trait> getTraitListFromDB(Trial trl) {
		//long trialID
		ArrayList<Trait> traitList = new ArrayList<Trait>();
		String whereClause = String.format(Locale.US, "%s = %d", TT_TRIALID, trl.getId());
		// MFK HACK alert. Don't return traits with type SYSTYPE_ATTRIBUTE:
		whereClause += String.format(" and %s != %d", TR_SYSTYPE, SYSTYPE_ATTRIBUTE);
		String qry = String.format("select * from %s join %s on %s = %s where %s",
				TABLE_TRAIT, TABLE_TRIALTRAIT,
				TT_TRAITID, TR_ID,
				whereClause);
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(qry, null);
			int numTraits = ccr.getCount();
			if (numTraits > 0) {
				ccr.moveToFirst();
				for (; numTraits > 0; --numTraits) {
					Datatype type = Datatype.fromInt(ccr.getInt(ccr.getColumnIndex(TR_TYPE)));
					String caption = ccr.getString(ccr.getColumnIndex(TR_CAPTION));
					String description = ccr.getString(ccr.getColumnIndex(TR_DESCRIPTION));
					String uploadURL = ccr.getString(ccr.getColumnIndex(TR_UPLOAD_URL));
					
					// barcode, null represented as -1:
					int barcodeAttId = -1;
					if (!ccr.isNull(ccr.getColumnIndex(TR_BARCODE)))
						barcodeAttId = ccr.getInt(ccr.getColumnIndex(TR_BARCODE));
					long id = ccr.getInt(ccr.getColumnIndex(TR_ID));
					Trait t = makeTrait(type, caption, description, uploadURL, barcodeAttId);
					if (t == null) return null;
					t.setId(id);
					t.SetFromDB(trl);
					
					t.mSysType = ccr.getInt(ccr.getColumnIndex(TR_SYSTYPE));
					t.mServerId = ccr.getInt(ccr.getColumnIndex(TR_SERVER_ID));
					traitList.add(t);
					ccr.moveToNext();
				}
			}
		} finally { if (ccr != null) ccr.close(); }
		return traitList;
	}

	
	/*
	 * DeleteTraitsLocalToTrial()
	 * Delete local traits for trial from db - should also be ad hoc?
	 */
	static void DeleteTraitsLocalToTrial(long trialId) {
		// Delete traits local to this trial (NB have to do this BEFORE deleting trial, else trialTrait records will be gone):
		String whereClause = String.format(Locale.US, "%s = %d and %s in (select %s from %s where %s = %d)", TR_SYSTYPE, Trait.SYSTYPE_TRIAL, TR_ID,
				TT_TRAITID, TABLE_TRIALTRAIT, TT_TRIALID, trialId);
		int gone = g_db().delete(TABLE_TRAIT, whereClause, null);
	}
	
	
	/*
	 * createEmptyTraitInstanceJSON()
	 * Returns a JSON object for the trait instance NOT including
	 * the trait instance data.
	 */
	protected JSONObject createEmptyTraitInstanceJSON(TraitInstance ti) throws JSONException {
		JSONObject jti = new JSONObject();
		jti.put(TI_TRAIT_ID, mServerId);		
		jti.put(TI_DAYCREATED, ti.getCreationDate());
		jti.put(TI_SEQNUM, ti.getExportSeqNum());  // Note must be EXPORTSeqNum
		jti.put(TI_SAMPNUM, ti.getSampleNum());
		return jti;
	}
	
	/*
	 * traitInstanceDataAsJSON()
	 * Returns the trait instance data as JSON object.
	 * NB could be called by background task, should be no UI calls.
	 * NB project name, trial_id, and trait_id are in the URL, not the JSON. 
	 * JSON format is:
	 *     {
	 *     "dayCreated":20130417,
	 *     "seqNum":1,
	 *     "sampleNum":1,
	 *     "data":[{"node_id":32, "timestamp":1234, "gps_long":1.33232, "gps_lat":3.454,
	 *              "userid":"abc123", "value":6}]
	 *     }
	 */
	JSONObject traitInstanceDataAsJSON(TraitInstance ti) throws JSONException {
		JSONObject jti = createEmptyTraitInstanceJSON(ti);
		
		// Add the data:
		JSONArray jdata = new JSONArray();
		Cursor ccr = null;
		try {		
			ccr = ti.getDatumCursor();
			if (ccr.moveToFirst())
				do {
					JSONObject jdatum = new JSONObject();
					jdatum.put(DM_NODE_ID, ccr.getInt(ccr.getColumnIndex(DM_NODE_ID)));
					jdatum.put(DM_TIMESTAMP, ccr.getLong(ccr.getColumnIndex(DM_TIMESTAMP)));
					jdatum.put(DM_GPS_LONG, ccr.getDouble(ccr.getColumnIndex(DM_GPS_LONG)));
					jdatum.put(DM_GPS_LAT, ccr.getDouble(ccr.getColumnIndex(DM_GPS_LAT)));
					jdatum.put(DM_USERID, ccr.getString(ccr.getColumnIndex(DM_USERID)));
					// Value field - NA indicated by absence of value:
					if (!ccr.isNull(ccr.getColumnIndex(DM_VALUE)))
						WriteDatumJSON(jdatum, ccr);
					jdata.put(jdatum);
				} while (ccr.moveToNext());
			jti.put("data", jdata);
		} finally { if (ccr != null) ccr.close(); }
		return jti;
	}
	

	/*
	 * NumericalRepSetStats()
	 * The RepSetStats() function is basically the same for integer and decimal,
	 * so has a common implementation here.
	 * MFK move to traitNumeric?
	 */
	public String NumericalRepSetStats(RepSet rs) {
		String q = String.format("select min(%s), max(%s), avg(%s), sum(%s * %s), count(*) from %s " +
				"where traitInstance_id in %s and %s is not null",
				DM_VALUE, DM_VALUE, DM_VALUE, DM_VALUE, DM_VALUE, TABLE_DATUM, rs.SQLFormatTI_IDList(), DM_VALUE);
		Cursor ccr = null;
		try {
			// Retrieve the system traits from the db:
			ccr = g_db().rawQuery(q, null);
			if (!ccr.moveToFirst()) return "Cannot retrieve stats";
			double mean = ccr.getDouble(2);
			String out = "";
			out += "Min: " + ccr.getDouble(0) + "\n";
			out += "Max: " + ccr.getDouble(1) + "\n";
			out += "Mean: " + mean + "\n";
			
			double sumSquares = ccr.getDouble(3);
			int count = ccr.getInt(4);
			double var; 
			if (count < 2)
				var = 0;
			else 
				var = (count / (count-1)) * (sumSquares / count - mean * mean);
			out += String.format("STD: %.2f\n", Math.sqrt(var));
			return out;
		} finally { if (ccr != null) ccr.close(); }
	}

	/*
	 * getBarcodeAttributeId()
	 * Note -1 indicates no barcode attribute.
	 * Ideally this would return the Attribute object (or null), but
	 * these may be available already in a Trial object, but we would
	 * need the Trial to get them. We could have Trial as a property
	 * of Trait - since we have exactly one trial for every trait now,
	 * and have relied on that assumption elsewhere. But that's not in
	 * place attow.
	 */
	public int getBarcodeAttributeId() {
		return mBarcodeAttId;
	}

}

