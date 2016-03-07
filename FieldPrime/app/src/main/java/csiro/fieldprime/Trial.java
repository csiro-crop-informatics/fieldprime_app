/*
 * Trial.java
 * Michael Kirk 2013
 * 
 * Trial class and sub classes.
 */

package csiro.fieldprime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import csiro.fieldprime.ActBluetooth.MyBluetoothDevice;
import csiro.fieldprime.Trait.Datatype;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import static csiro.fieldprime.DbNames.*;
import static csiro.fieldprime.Trait.Datatype.*;
import static csiro.fieldprime.Util.flog;

/*
 * Class Trial
 * Memory representation of a trial.
 */
public class Trial {
	// CONSTANTS: =====================================================================================================
	private static final int NOT_SET_YET = -1;
	
	// Trial Property names:
	public static final String NODE_CREATION = "nodeCreation";

	// Known attribute names:
	public static final String REP_ATTRIBUTE_NAME = "Replicate";

	// INSTANCE DATA: ==================================================================================================

	// Fields from the trial table:
	// MFK - probably should get rid of separate server id, in same way as for node and trait
	// But what then if we deal with multiple servers?
	private long m_id = -1; // This is auto generated local id, may not match the server ID, -1 indicates not in DB yet.
	private String mName;	
	private String mUploadURL = null;    // when null, indicates trial not from server
	private String mAdhocURL;
	private String mServerToken;  // Token provided by server, included in subsequent communication with the server. 
								  // MFK get rid of mServerToken, should be in the HATEOAS URLs
	private String mFilename;     // CSV filename if trial created from CSV file.
	private String mSite;
	private String mYear;
	public String mAcronym;
	
	private Pstate pstate = new Pstate();  // Scoring screen state for trial

	/*
	 * Trial Lists:
	 * Note these lists should always exist in memory while a trial exists and before it is used.
	 */
	private ArrayList<Trait> mTraitList = new ArrayList<Trait>(); // The list of traits USED BY THIS trial (i.e. not all traits).
	final private ArrayList<Node> mFullNodeList = new ArrayList<Node>();
	private ArrayList<Node> mNodeList = mFullNodeList;
	private ArrayList<NodeAttribute> mAttributeList = new ArrayList<NodeAttribute>();
	private ArrayList<RepSet> mScoreSets = new ArrayList<RepSet>();  // List of scoresets for this trial

	private int mNodeIndex = -1;  // current (zero based) position in the node list.
	
	public ArrayList <MyBluetoothDevice> mBtDeviceList; // This here as the list needs to be stored between calls for BluetoothActivity

	// If false then no row and column fields are shown in the scoring screen. This for a possibly one-off
	// need to hide the row/col, but in general we should move away from identifying nodes by row/col
	public boolean mShowRowCol = true;
	
	private String [] mIndexNames = {"Row", "Col"};
	
	/*
	 * Create a NodeProperty instance for the following four node members:
	 * Perhaps should be enum, but these may be deprecated at some stage..
	 * NB - these values are used as indexes into mFixedTups.
	 */
	public static final int FIELD_ROW = 0;
	public static final int FIELD_COL = 1;
	public static final int FIELD_BARCODE = 2;
	public static final int FIELD_LOCATION = 3;
	private ArrayList<NodeProperty> mFixedTups = new ArrayList<NodeProperty>();
	
	// STATIC METHODS: ===============================================================================================

	static private SQLiteDatabase g_db() {
		return Globals.g.db();
	}

	/*
	 * CreateLocalTrial()
	 * Creates NEW local trial, stores it in the db.
	 */
	static public Result CreateLocalTrial(String filename, String name, String year, String site, String acronym) {
		Trial ds = new Trial(filename, name, site, year, acronym);
		return ds.insertDB();
	}

	/*
	 * getSavedTrial()
	 * Used as a constructor to create trial object given name.
	 * Create Trial instance from named trial in database.
	 * NB fills the lists of traits, traitInstance, and nodes.
	 * NB nodes are not sorted. (perhaps they should be, to preference default)?
	 */
	static public Result getSavedTrial(String tname) {
		// Get trial table data:
		Cursor ccr = null;
		Trial trl;
		try {
			ccr = g_db().query(TABLE_TRIAL, new String[] { DS_ID, DS_SERVER_ID, DS_SERVER_TOKEN, DS_NAME, DS_FILENAME,
					DS_SITE, DS_YEAR, DS_ACRONYM, DS_UPLOAD_URL, DS_ADHOC_URL }, "name = '" + tname + "'", null, null, null, null);
			if (ccr.getCount() != 1) // Return if no trials found:
				return new Result(false, "Trial " + tname + " not found.");
			ccr.moveToFirst();
			trl = new Trial();
			trl.m_id = ccr.getInt(ccr.getColumnIndex(DS_ID));
			trl.mServerToken = ccr.getString(ccr.getColumnIndex(DS_SERVER_TOKEN));
			trl.setName(ccr.getString(ccr.getColumnIndex(DS_NAME)));
			trl.mFilename = ccr.getString(ccr.getColumnIndex(DS_FILENAME));
			trl.mSite = ccr.getString(ccr.getColumnIndex(DS_SITE));
			trl.mYear = ccr.getString(ccr.getColumnIndex(DS_YEAR));
			trl.mAcronym = ccr.getString(ccr.getColumnIndex(DS_ACRONYM));
			trl.mUploadURL = ccr.getString(ccr.getColumnIndex(DS_UPLOAD_URL));
			trl.mAdhocURL = ccr.getString(ccr.getColumnIndex(DS_ADHOC_URL));
		} finally { if (ccr != null) ccr.close(); }

		trl.setNodeAttributeListFromDB();
		trl.mTraitList = Trait.getTraitListFromDB(trl);   // NB, need mAttributeList to be set up before this call
		trl.fillTrialNodesFromDB();
		
		// Set the index names if specified:
		String indname = trl.getPropertyValue(TRIAL_INDEX_NAME1);
		if (indname != null) {
			trl.mIndexNames[0] = indname;
		}
		indname = trl.getPropertyValue(TRIAL_INDEX_NAME2);
		if (indname != null)
			trl.mIndexNames[1] = indname;
		
		// Set up the fixed properties:
		trl.mFixedTups.add(NodeProperty.newFixedInstance(trl, trl.getIndexName(0), FIELD_ROW, T_INTEGER));
		trl.mFixedTups.add(NodeProperty.newFixedInstance(trl, trl.getIndexName(1), FIELD_COL, T_INTEGER));
		trl.mFixedTups.add(NodeProperty.newFixedInstance(trl, "Barcode", FIELD_BARCODE, T_STRING));
		trl.mFixedTups.add(NodeProperty.newFixedInstance(trl, "Location", FIELD_LOCATION, T_LOCATION));

		// Get trial ready for use.
		trl.initScoreSetList();
		trl.setNodeIndex(0);

		return new Result(trl);
	}
	static public boolean startScoringActivity(Context ctx, String name) {
		// Only open if not busy, we only require not busy on this trial, but safer
		// to wait til not busy with anything I think.
		if (Globals.isBusy())
			return false;
		
		Intent intent = new Intent(ctx, ActTrial.class);
		intent.putExtra(ActTrial.ACTSCORING_TRIALNAME, name);
		ctx.startActivity(intent);
		return true;
	}
	/*
	 * DeleteTrial()
	 * It should sufficient just to delete trial, to the extent that the DB is configured to automatically clean up dependents.
	 * The trait table is an exception, since system traits can exist independently of trials. Local traits will need
	 * to be explicitly removed
	 */
	static public void DeleteTrial(long trialId) {
		// Delete traits local to this trial (NB have to do this BEFORE deleting trial, else trialTrait records will be gone):
		Trait.DeleteTraitsLocalToTrial(trialId); // MFK all traits should be local now

		// Start delete cascade by deleting from the trial table:
		String whereClause = String.format(Locale.US, "%s = %d", DS_ID, trialId);
		int gone = g_db().delete(TABLE_TRIAL, whereClause, null);
	}

	/*
	 * GetTrialList()
	 * Returns list of the names of trials currently in the database, or null if there isn't any.
	 * If non null trialIDs is provided (assumed empty), then this will be set to the trial IDs.
	 */
	static public String[] GetTrialList(ArrayList<Long> trialIDs) {
		Cursor ccr = null;
		try {
			ccr = g_db().query(TABLE_TRIAL, new String[] { DS_NAME, DS_ID }, null, null, null, null, null);
			int numTrials = ccr.getCount();
			// Notify and return if no trials found:
			if (numTrials <= 0) {
				return null;
			}
			String[] names = new String[numTrials];
			ccr.moveToFirst();
			for (int i = 0; i < numTrials; ++i) {
				names[i] = ccr.getString(ccr.getColumnIndex(DS_NAME));
				if (trialIDs != null)
					trialIDs.add(ccr.getLong(ccr.getColumnIndex(DS_ID)));
				ccr.moveToNext();
			}
			return names;
		} finally { if (ccr != null) ccr.close(); }
	}

	/*Trial
	 * ImportCSVTrialFile()
	 * Create new trial from a file. Only writes to db? I.e. doesn't open trial.
	 * NB Not fully supported, as recommended use is now via server.
	 * Non-standard attributes are not recorded. 
	 * Reps will (quite likely) not work.
	 */
	static public Result ImportCSVTrialFile(String fname) {
		File tplate = new File(fname);
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(tplate));
			String line = br.readLine(); // read header line:
			if (line == null)
				return new Result(false, "Invalid trial file (" + fname + ")");

			// find indicies of columns with various headers
			String[] hdrs = line.split("\\s*,\\s*");
			int NUM_KNOWN_COLS = 0;
			final int ROW = NUM_KNOWN_COLS++;
			final int COLUMN = NUM_KNOWN_COLS++;
			final int DESCRIPTION = NUM_KNOWN_COLS++;
			final int BARCODE = NUM_KNOWN_COLS++;
			final int NAME = NUM_KNOWN_COLS++;
			final int YEAR = NUM_KNOWN_COLS++;
			final int ACRONYM = NUM_KNOWN_COLS++;
			final int REP = NUM_KNOWN_COLS++;
			int maxIndex = -1;
			String[] attNames = { "row", "column", "description", "barcode", "name", "year", "acronym", "rep" };
			int[] indexes = new int[NUM_KNOWN_COLS];
			Arrays.fill(indexes, -1);
			// See what headers match attNames:
			for (int ind = 0; ind < hdrs.length; ++ind) {
				for (int j = 0; j < attNames.length; ++j) {
					if (hdrs[ind].toLowerCase(Locale.US).contains(attNames[j])) {
						if (indexes[j] >= 0) {
							return new Result(false, "Multiple column headers containing " + attNames[j]);
						}
						indexes[j] = ind;
						if (ind > maxIndex)
							maxIndex = ind;
					}
				}
			}
			// Check we have all the essential columns:
			for (int x : new int[] { ROW, COLUMN, NAME, YEAR, ACRONYM }) {
				if (indexes[x] < 0) {
					return new Result(false, "No column header found containing " + attNames[x]);
				}
			}

			// Now process the data lines:
			boolean first = true;
			NodeAttribute repAtt = null;
			Trial trl = null;
			while ((line = br.readLine()) != null) {
				String[] fields = line.split("\\s*,\\s*", -1);
				if (fields.length < maxIndex + 1)
					return new Result(false, "Line found with too few columns");

				if (first) { // first time through we grab the trial attributes and create trial.
					// MFK note we should probably not create in db yet, but do everything in mem until
					// successfully finished, and then save the trial and its components to the database.
					// Need to check that existing code is not relying on stuff being in db however.
					String sYear = fields[indexes[YEAR]].replaceAll("^\"|\"$", "");
					String sName = fields[indexes[NAME]].replaceAll("^\"|\"$", "");
					String sAcronym = fields[indexes[ACRONYM]].replaceAll("^\"|\"$", "");
					if (sYear.length() < 2)
						return new Result(false, "Invalid or missing trial year found on first data line");
					if (sName.length() < 2)
						return new Result(false, "Invalid or missing trial name found on first data line");
					if (sAcronym.length() < 2)
						return new Result(false, "Invalid or missing trial acronym found on first data line");
					Result res = Trial.CreateLocalTrial(fname, sName, sYear, "", sAcronym);
					if (res.bad())
						return res;
					else
						trl = (Trial) res.obj();
					if (indexes[REP] >= 0) { // If rep is there, create a virtual column for it.
						// MFK note crazy hack, ids are expected to be from server, but here there
						// is no server, and no other user attributes, so we'll try using the trial
						// id times -1. So as to be unique per trial, and not to over lap with any
						// ids from server. I'm not going to test, since this functionality should
						// be deprecated.
						repAtt = trl.createAttribute(trl.getId() * -1, REP_ATTRIBUTE_NAME, T_STRING, 0);
						if (repAtt == null)
							return new Result(false, "Cannot create Replicate attribute");
					}
					first = false;
				}

				String sRow = fields[indexes[ROW]].replaceAll("^\"|\"$", "");
				String sCol = fields[indexes[COLUMN]].replaceAll("^\"|\"$", "");

				String sDesc = "";
				if (indexes[DESCRIPTION] >= 0)
					sDesc = fields[indexes[DESCRIPTION]].replaceAll("^\"|\"$", "");

				String sBarcode = "";
				if (indexes[BARCODE] >= 0)
					sDesc = fields[indexes[BARCODE]].replaceAll("^\"|\"$", "");

				int row = 0;
				int col = 0;
				if (Util.isInteger(sRow))
					row = Integer.parseInt(sRow);
				else
					return new Result(false, "Row value (" + sRow + ") is not a valid integer");
				if (Util.isInteger(sCol))
					col = Integer.parseInt(sCol);
				else
					return new Result(false, "Column value (" + sCol + ") is not a valid integer");

				Result res = trl.addNode(row, col, sDesc, sBarcode);
				if (res.bad())
					return res;
				if (indexes[REP] >= 0) {
					Trial.Node tu = (Trial.Node) res.obj();
					res = tu.setAttributeValue(repAtt, fields[indexes[REP]].replaceAll("^\"|\"$", ""));
					if (res.bad()) {
						Trial.DeleteTrial(trl.getId());
						return res;
					}
				}

			}
			return new Result(true, trl.getName());

		} catch (IOException e) {
			return new Result(false, "Cannot read from file");
		} finally {
			if (br != null){
				try {
					br.close();
				} catch (IOException e) {
					//don't care
				}
			}
		}
	}
	
	/*
	 * checkTrialExists()
	 * Checks if trial with given name already in db.
	 * Returns 0 for no, 1 for yes, -1 for db problem.
	 */
	static public int checkTrialExists(String name) {
		// Check trial with than name is not already in the database:
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(String.format("select count(*) from %s where %s = '%s'", TABLE_TRIAL, DS_NAME, name), null);
			if (!ccr.moveToFirst())
				return -1;
			if (ccr.getInt(0) == 0)
				return 0;
			else
				return 1;
		} finally { if (ccr != null) ccr.close(); }
	}
	
	/*
	 * ImportJSONTrial()
	 * Create new trial from a JSON object.
	 * NB: Since the JSON is assumed to come from a server, we'll skip error checking.
	 * NB2: Note names in json object are currently hard-coded and must match those generated by the server.
	 * NB3: This should be run in the background. 
	 * NB4: On error return, the trial will (hopefully) have been deleted:
	 * NB5: This could be integrated with MainActivity.GetTrialFromServer()
	 * 
	 * Sample JSON trial:
	 * {"site":"Vineyard","id":"4","acronym":"x123456","serverToken":"be7af8181fd68528","name":"Clonakilla","year":"2013",
	 * "traits":[{"min":"0","id":"1","unit":"centimetres","sysType":"1","max":"200","description":"Height from ground to tip","caption":"Height","tid":null,"type":"1"},
	 *           {"min":"20130101","id":"10","unit":"Date","sysType":"1","max":"20131231","description":"Date of Flowering (or estimate of when flowering did\/will occur)","caption":"Flowering Date","tid":null,"type":"5"}],
	 * "trialUnits":[{"id":"6636","pedigree":"french","col":"4","trial_id":"4","description":"dangerous","barcode":"12","genotype":"muscat","row":"3"},
	 *               {"id":"6635","pedigree":"french","col":"3","trial_id":"4","description":"blousy","barcode":"11","genotype":"chardonnay","row":"3"},
	 *               ...
	 *               {"id":"6625","pedigree":"french","col":"1","trial_id":"4","description":"spicy","barcode":"1","genotype":"shiraz","row":"1"}]
	 * }  (NB "trialUnits" should become "nodes"
	 * 
	 * MFK should have trial options where each option is an object, but it has a name or
	 * an id, and the receiver can ignore it if they don't support the option.
	 * Options:
	 * Row name, Col name, show row, show col.
	 * Users should be able, perhaps, to show the
	 * 
	 * 
	 */
	static public Result ImportJSONTrial(JSONObject json, ActMain.ShowProgress progFunc)
			throws JSONException {
		String year = json.getString(JTRL_YEAR);
		String site = json.getString(JTRL_SITE);
		String name = json.getString(JTRL_NAME);
		String acronym = json.getString(JTRL_ACRONYM);

		// Check trial with that name is not already in the database:
		switch (checkTrialExists(name)) {
		case 1: return new Result(false, "Database access problem");
		case -1: return new Result(false, "Cannot import trial as it has already been imported");
		}

		/*
		 *  Make new trial and write to db:
		 *  
		 *  MFK. Note if we want to be able to update things like trial name, URLs, or whatever,
		 *  we could move the code to read these things from the json to FillOrUpdateJSONTrial
		 *  and write them there. Note however that ATTOW there is no update function for trial,
		 *  and the name check above would have to be moved there as well.
		 */
		Trial trial = new Trial("server", name, site, year, acronym);
		Result res = trial.insertDB();
		if (res.bad())
			return res;

		progFunc.showProgress("About to fill");
		res = trial.fillOrUpdateJSONTrial(json, false, progFunc);
		if (res.bad()) {
			Trial.DeleteTrial(trial.getId());
			return res;
		}
		return res;
	}
	
	
	// FUNCTIONS: ================================================================================================

	private Trial () {}
	
	/*
	 * Constructor
	 * Create a new Trial in memory, it is NOT added to the db.
	 * The db is referenced, however, to make sure the name for the new trial will be unique when it is add to
	 * the db. So I guess that means it should be added immediately, if the trial to be created is to eventually
	 * be added (else some other code may get the new name).
	 */
	private Trial(String filename, String name, String site, String year, String acronym) {
		this.setName(name);
		this.mFilename = filename;
		this.mSite = site;
		this.mYear = year;
		this.mAcronym = acronym;
	}
	
	public long getId() {
		return m_id;
	}

	boolean isFromServer() {
		return mUploadURL != null;
	}

	public String getUploadURL() {
		return mUploadURL;
	}
	
	public String getAdhocURL() {
		return mAdhocURL;
	}

	public String getIndexName(int num) {
		return mIndexNames[num];
	}
	
	public Pstate getPstate() { return pstate; }


	/*
	 * storageDir()
	 * Returns a storage directory for this trial. The directory is created if necessary.
	 * Currently just a directory with the name of the trial, this may have to be extended
	 * to support multiple dbs, eg by having it under a dir for the db.
	 */
	public String storageDir() {
		String spath = Globals.g.getAppRootDir() + mName;
		File sdir = new File(spath);
		if (!sdir.isDirectory())
			sdir.mkdir();
		return spath;
	}
	
	/*
	 * insertDB()
	 */
	private Result insertDB() {
		// Check constraints (attow just that name is unique):
		String whereClause = String.format("%s = '%s'", DS_NAME, mName);
		long num = DatabaseUtils.queryNumEntries(g_db(), TABLE_TRIAL, whereClause);
		if (num != 0) {
			return new Result(false, String.format("Trial with name %s already exists", mName));
		}

		ContentValues values = new ContentValues();
		values.put(DS_NAME, getName());
		values.put(DS_FILENAME, mFilename);
		values.put(DS_SITE, mSite);
		values.put(DS_YEAR, mYear);
		values.put(DS_ACRONYM, mAcronym);
		values.put(DS_UPLOAD_URL, mUploadURL);
		values.put(DS_ADHOC_URL, mAdhocURL);
		if (mServerToken != null)
			values.put(DS_SERVER_TOKEN, mServerToken);
		m_id = g_db().insert(TABLE_TRIAL, null, values);
		if (m_id < 0) {
			return new Result(false, "Failed creation of new trial: " + getName());
		}
		return new Result(this);
	}
	
	/*
	 * updateDB()
	 * Rewrite a few fields to the DB.
	 */
	private Result updateDB() {
		ContentValues values = new ContentValues();
		values.put(DS_NAME, getName());
		values.put(DS_FILENAME, mFilename);
		values.put(DS_SITE, mSite);
		values.put(DS_YEAR, mYear);
		values.put(DS_ACRONYM, mAcronym);
		values.put(DS_UPLOAD_URL, mUploadURL);
		values.put(DS_ADHOC_URL, mAdhocURL);
		if (mServerToken != null)
			values.put(DS_SERVER_TOKEN, mServerToken);
		g_db().update(TABLE_TRIAL, values, String.format("%s = %d", DS_ID, getId()), null);
		return new Result();
	}

	/*
	 * getPropertyValue()
	 * Return value of trial property with given name, or null if there is none.
	 * Note these currently retrieved from db, but perhaps would be better cached
	 * in the trial object.
	 */
	public String getPropertyValue(String name) {
		String x = String.format(Locale.US, "select %s from %s where %s = %d and %s = '%s'", TA_VALUE, TABLE_TRIAL_PROPERTY,
				TA_TRIAL_ID, getId(), TA_NAME, name);
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(x, null);
			if (ccr.moveToFirst())
				return ccr.getString(0);
			else
				return null;
		} catch (Exception e) {
			return null;  // we're assuming table doesn't exist, eg old client database. can remove this later..
		} finally { if (ccr != null) ccr.close(); }
	}
	
	/*
	 * getBooleanPropertyValue()
	 * Returns value of property assumed to represent a boolean.
	 * Missing, or anything but the string value "true" returns false.
	 */
	public boolean getBooleanPropertyValue(String name) {
		String x = getPropertyValue(name);
		if (x == null)
			return false;
		return x.equals("true");
	}
	
	/*
	 * saveTrialPropertyValue()
	 * Store name/value pair for this trial in database.
	 * Note this should only be used to store data coming from the server.
	 */
	private boolean saveTrialPropertyValue(String name, String value) {
		ContentValues values = new ContentValues();
		values.put(TA_TRIAL_ID, getId());
		values.put(TA_NAME, name);
		values.put(TA_VALUE, value);
		if (g_db().insertWithOnConflict(TABLE_TRIAL_PROPERTY, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
			return false;
		return true;
	}

	/*
	 * setNodeAttributeListFromDB()
	 * Set mAttributeList according to the current db contents.
	 */
	private void setNodeAttributeListFromDB() {
		mAttributeList = new ArrayList<NodeAttribute>();
		String x = String.format(Locale.US, "select %s,%s,%s,%s from %s where %s = %d", TUA_ID, TUA_NAME, TUA_DATATYPE, TUA_FUNC,
				TABLE_NODE_ATTRIBUTE, TUA_TRIAL_ID, getId());
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(x, null);
			int numAtts = ccr.getCount();
			if (numAtts > 0) {
				ccr.moveToFirst();
				for (; numAtts > 0; --numAtts) {
					NodeAttribute att = new NodeAttribute(ccr.getInt(0), ccr.getString(1), Datatype.fromInt(ccr.getInt(2)), ccr.getInt(3));
					mAttributeList.add(att);
					ccr.moveToNext();
				}
			}
		} finally { if (ccr != null) ccr.close(); }
	}

	/*
	 * createAttribute()
	 * Create attribute in db, and add to the attribute list.
	 * Returns null on failure.
	 */
	private NodeAttribute createAttribute(long aid, String name, Datatype datatype, int func) {
		ContentValues values = new ContentValues();
		values.put(TUA_ID, aid);
		values.put(TUA_TRIAL_ID, getId());
		values.put(TUA_NAME, name);
		values.put(TUA_DATATYPE, datatype.value());
		values.put(TUA_FUNC, func);
		long id = g_db().insert(TABLE_NODE_ATTRIBUTE, null, values);
		if (id < 0)
			return null;
		NodeAttribute att = new NodeAttribute(id, name, datatype, func);
		mAttributeList.add(att);
		return att;
	}

	/*
	 * AttributePresent()
	 * Return whether an attribute of the specified name exists for this trial.
	 * MFK note case insensitive comparison.
	 */
	public boolean AttributePresent(String attname) {
		for (NodeAttribute att : mAttributeList) {
			if (attname.equalsIgnoreCase(att.name()))
				return true;
		}
		return false;
	}
	
	/*
	 * AddAdHocTrait()
	 * Add an ad hoc trait to the trial (and database).
	 */
	public Result AddAdHocTrait(String caption, String description, Datatype type, String tid, String unit, Double min, Double max) {
		Result res = Trait.NewTrait(Trait.SYSTYPE_ADHOC, this, 0, caption, description, type, null, -1);
		if (res.bad())
			return res;
		Trait nt = (Trait) res.obj();
		mTraitList.add(nt);
		return new Result();
	}
	

	//*** Node Stuff: **************************************************************************

	/*
	 * gotoNodeByBarcode()
	 * Find node by barcode in current list.
	 * If this becomes too slow, we could search based on the current ordering, or maintain an index list of some kind.
	 */
	public boolean gotoNodeByBarcode(String barcode) {
		for (int i = 0; i < mNodeList.size(); ++i) {
			Node tu = mNodeList.get(i);
			if (tu.mBarcode.equalsIgnoreCase(barcode)) {
				setNodeIndex(i);
				return true;
			}
		}
		return false;
	}
	
	/*
	 * gotoNodebyId()
	 * Find node by node id in current list.
	 * If this becomes too slow, we could search based on the current ordering, or maintain an index list of some kind.
	 */
	public boolean gotoNodebyId(long id) {
		for (int i = 0; i < mNodeList.size(); ++i) {
			Node tu = mNodeList.get(i);
			if (tu.getId() == id) {
				setNodeIndex(i);
				return true;
			}
		}
		return false;
	}

	/*
	 * Node index getter/setters.
	 * Node index is zero base index position of current node in the current node list.
	 */
	public int getNodeIndex() {
		return mNodeIndex;
	}
	

	private void setNodeIndex(int index) {
		this.mNodeIndex = index;
	}

	/*
	 * gotoNextNode()
	 * Move to the next node in the list. Since the list is sorted according to the currently
	 * chosen walk-order, we just have to increment the index, unless already at end.
	 * Returns true if next node exists, or false if already at last.
	 */
	public boolean gotoNextNode() {
		int last = mNodeList.size() - 1;
		if (mNodeIndex == last)
			return false;
		++mNodeIndex;
		return true;
	}

	/*
	 * gotoPrevNode()
	 * Move to the previous node in the list. Since the list is sorted according to the
	 * currently chosen walk-order, we just have to decrement the index, unless already at first.
	 * Returns true if prev node exists, or false if already at first.
	 */
	public boolean gotoPrevNode() {
		if (mNodeIndex == 0)
			return false;
		--mNodeIndex;
		return true;
	}
	
	/*
	 * getCurrNode() Returns the current node.
	 */
	public Node getCurrNode() {
		if (mNodeIndex < 0)
			return null;
		return mNodeList.get(mNodeIndex);
	}

	/*
	 * getNodeByIndex()
	 * Returns the current node with the given index.
	 */
	public Node getNodeByIndex(int index) {
		return mNodeList.get(index);
	}
	
	/*
	 * getNodeById() 
	 * Returns the current node with the given id, or null if not found IN CURRENT FILTERED SET.
	 * NB - if performance becomes an issue, could perhaps use a hashmap or similar.
	 */
	public Node getNodeById(long id) {
		for (Node tu : mNodeList) {
			if (tu.mId == id)
				return tu;
		}
		return null;
	}

	/*
	 * getMatchingNodes()
	 * Returns list of TuAtt matching passed search parameters.
	 * Search is for AND of specified conditions.
	 * . For row and col -1 indicates any.
	 * . If attribute is set, but no search text, then the search is for nodes that have a value
	 *   for the specified attribute. 
	 * . If search text is specified, but no attribute, then the search is for nodes that have a value
	 *   for any attribute which starts with the specified text.
	 * . If both search text and attribute specified then search is for nodes that have a value
	 *   for that attribute starting with the specified text.
	 */
	class TuAtt {
		long tuid;
		int  row;
		int  col;
		String atName;
		String atVal;
		TuAtt(long id, int row, int col, String atName, String atVal) {
			this.tuid = id;
			this.row = row;
			this.col = col;
			this.atName = atName;
			this.atVal = atVal;
		}
	}

	/**
	 * Get one node matching row col. Null if none found.
	 * @param row index 1 value
	 * @param col index 2 value
	 * @return First found node, or null if none found with specified values
	 */
	public Node getNodeByRowCol(int row, int col) {
		for (int i = 0; i < mNodeList.size(); ++i) {
			Node nd = mNodeList.get(i);
			if (nd.mRow == row && nd.mCol == col)
				return nd;
		}
		return null;
	}
	public ArrayList<TuAtt> getMatchingNodes(int row, int col, NodeAttribute att, String searchTxt) {
		ArrayList<TuAtt> foundNodes = new ArrayList<TuAtt>();
		boolean bcol = col >= 0;
		boolean brow = row >= 0;
		boolean bAtt = att != null;
		boolean bTxt = searchTxt != null && searchTxt.length() > 0;
		if (!brow && !bcol && !bAtt && !bTxt)
			return foundNodes;

		if (!bAtt && !bTxt) {  // => brow || bcol
			// handle simpler case with no attribute or search text here:
			// Loop over nodes, adding those that match row AND col: 
			for (int i = 0; i < mNodeList.size(); ++i) {
				Node tu = mNodeList.get(i);
				if (brow && tu.mRow != row)
					continue;
				if (bcol && tu.mCol != col)
					continue;
				foundNodes.add(new TuAtt(tu.getId(), tu.getRow(), tu.getCol(), "", ""));
			}
		} else if (bAtt) {
			String qry = String.format(Locale.US,
					"select tu.%s, %s, %s, %s" +
					" from %s tu, %s av" +
					" where tu.%s = %d and tu.%s = av.%s and av.%s = %d",
					ND_ID, ND_ROW, ND_COL, AV_VALUE,
					TABLE_NODE, TABLE_ATTRIBUTE_VALUE,
					ND_TRIAL_ID, this.getId(), ND_ID, AV_NODE_ID, AV_NATT_ID, att.id());
			if (brow) qry += String.format(" AND row = %d", row);
			if (bcol) qry += String.format(" AND col = %d", col);
			if (bTxt)
				qry += String.format(" AND av.%s like '%s%%' collate nocase", AV_VALUE, searchTxt);

			Cursor ccr = null;
			try {
				ccr = g_db().rawQuery(qry, null);
				if (ccr.moveToFirst())
					do {
						TuAtt f = new TuAtt(ccr.getLong(ccr.getColumnIndex(ND_ID)),
								ccr.getInt(ccr.getColumnIndex(ND_ROW)),
								ccr.getInt(ccr.getColumnIndex(ND_COL)),
								att.name(),
								ccr.getString(ccr.getColumnIndex(AV_VALUE)));
						foundNodes.add(f);
					} while (ccr.moveToNext());
			} finally { if (ccr != null) ccr.close(); }
		} else {
			// Version where we search for text in any attribute 
			// Note we can return multiple instances of the same node (if it hits with multiple attributes)
			String qry = String.format(Locale.US,
					"select tu.%s, %s, %s, %s, %s" +
					" from %s tu, %s tua, %s av" +
					" where tu.%s = %d and tu.%s = av.%s and tua.%s = av.%s",
					ND_ID, ND_ROW, ND_COL, TUA_NAME, AV_VALUE,
					TABLE_NODE, TABLE_NODE_ATTRIBUTE, TABLE_ATTRIBUTE_VALUE,
					ND_TRIAL_ID, this.getId(), ND_ID, AV_NODE_ID, TUA_ID, AV_NATT_ID);
			if (brow) qry += String.format(" and row = %d", row);
			if (bcol) qry += String.format(" and col = %d", col);
			qry += String.format(" and av.%s like '%s%%' collate nocase", AV_VALUE, searchTxt);			
			Cursor ccr = null;
			try {
				ccr = g_db().rawQuery(qry, null);
				if (ccr.moveToFirst())
					do {
						TuAtt f = new TuAtt(ccr.getLong(ccr.getColumnIndex(ND_ID)),
								ccr.getInt(ccr.getColumnIndex(ND_ROW)),
								ccr.getInt(ccr.getColumnIndex(ND_COL)),
								ccr.getString(ccr.getColumnIndex(TUA_NAME)),
								ccr.getString(ccr.getColumnIndex(AV_VALUE)));
						foundNodes.add(f);
					} while (ccr.moveToNext());
			} finally { if (ccr != null) ccr.close(); }
		}
		return foundNodes;
	}

	/*
	 * getMatchingNodes()
	 * NB doesn't rely on Trial..
	 * ..which presumably suggests this should be a NodeAttribute method.
	 */
	public ArrayList<Long> getMatchingNodes(NodeAttribute att, String searchTxt) {
		ArrayList<Long> nlist = new ArrayList<Long>();
		String qry = String.format(Locale.US,
				"select %s" +
				" from %s" +
				" where %s = %d" +
				" AND %s = '%s' collate nocase",
				AV_NODE_ID, TABLE_ATTRIBUTE_VALUE, AV_NATT_ID, att.id(), AV_VALUE, searchTxt);
		Cursor ccr = null;
		try {		
			ccr = g_db().rawQuery(qry, null);
			if (ccr.moveToFirst())
				do {
					nlist.add(ccr.getLong(ccr.getColumnIndex(AV_NODE_ID)));
				} while (ccr.moveToNext());
		} finally { if (ccr != null) ccr.close(); }
		return nlist;
	}
	
	/*
	 * addNodeToList()  XXX NOTE MAY NEED TO BE ADDED TO FULL NODE LIST
	 *                  XXX SHOULD BE OK AS THIS ONLY CALLED IN TRIALS WHICH ARE NOT SCORED?
	 * Add node to list.
	 */
	private void addNodeToList(Node tu) {
		mFullNodeList.add(tu);
	}

	/*
	 * addNode()
	 * Add a node to the trial and database.
	 * NB - this should NOT be called on a trial in use. I.e. it must be reopened.
	 * MFK it would be nice to get the local stuff out of the Node constructors.
	 */
	private Result addNode(int id, int row, int col, String description, String barcode, Location location) {
		Node tu = new Node(id, 0, row, col, description, barcode, location);
		addNodeToList(tu);
		if (tu.InsertOrUpdateDB() < 0)
			new Result(false, "Cannot add node to database");
		return new Result(tu);
	}

	/*
	 * addLocalNode()    XXX NOT ADDING TO FULL NODE LIST SO MAY BE LOST THRU FILTER CHANGES
	 *                   XXX NOTE CURRENTLY EXPLICITLY DISALLOWING WHILE FILTERING ON
	 * Create and add to the trial a "local" or "new" node.
	 */
	public Result addLocalNode() {
		if (pstate.isFiltering()) { // May be a problem with filtering in place, so disable for now..
			return new Result(false, "Disable filtering before adding new nodes");
		}
		Integer NewLocalId = Tstore.TRIAL_NEXT_LOCAL_ID.getAndIncrementIntValue(
				g_db(), (int)getId(), (int)Database.MIN_LOCAL_ID);
		if (NewLocalId == null)
			return new Result(false, "Cannot get new local id");
		
		Node node = new Node(NewLocalId, this.getNumLocalNodes() + 1, -1, -1, "", "", null);
		// Insert as the last NOT_SET_YET at the beginning of the list
		// should make all walk orders keep these at the beginning
		int pos = 0;
		for (; pos<mFullNodeList.size(); ++pos) {
			if (!mFullNodeList.get(pos).isLocal())
				break;
		}
		mFullNodeList.add(pos, node);
		
		if (node.InsertOrUpdateDB() < 0)
			new Result(false, "Cannot add node to database");
		return new Result(node);
	}
	
	// MFK only used for csv, i think, gps location not supported.
	public Result addNode(int row, int col, String description, String barcode) {
		return addNode(NOT_SET_YET, row, col, description, barcode, null);
	}

	/*
	 * getNumNodes()
	 * Return the number of nodes in the db.
	 */
	public long getNumNodes() {
		String whereClause = String.format(Locale.US, "%s = %d", ND_TRIAL_ID, getId());
		return DatabaseUtils.queryNumEntries(g_db(), TABLE_NODE, whereClause);
	}
	
	/*
	 * getNumLocalNodes()
	 * Return the number of nodes in the db.
	 */
	public int getNumLocalNodes() {
		String whereClause = String.format(Locale.US, "%s = %d and %s >= %d", ND_TRIAL_ID, getId(), ND_ID, Database.MIN_LOCAL_ID);
		return (int) DatabaseUtils.queryNumEntries(g_db(), TABLE_NODE, whereClause);  // 2 billion is unlikely to be reached.
	}

	//*** Filtering Stuff: **************************************************************************

	/*
	 * setFilter()
	 * This needed (rather than doing this from ActTrial), because of the need to touch
	 * a bunch of private Trial stuff.
	 */
	public void setFilter(NodeAttribute att, String attval) {
		pstate.setFilter(att, attval);
		if (att == null) {
			mNodeList = mFullNodeList;// NB Here is the only place an existing filter is removed
			pstate.clearFilter();
			Util.toast("No attribute selected, no filter applied. " + mNodeList.size() + "nodes in set");
		} else {
			pstate.setFilter(att, attval);
			applyFilter(pstate);
		}
		setNodeIndex(0);
		pstate.sortList(null, this);
	}

	/*
	 * applyFilter()
	 * NB after filter caller may need to reapply sort.
	 */
	private void applyFilter(Pstate ps) {
		int count = 0;
		if (!ps.isFiltering()) {
			Util.toast("No filter specified");
			return;
		}
		NodeAttribute nat = ps.getFilterAttribute();
		String attvalue = ps.getFilterAttValue();
		if (nat == null || attvalue == null) return;

		ArrayList<Node> newNodeList = new ArrayList<Node>();
		// MFK note may be filtering already filtered set, not the same as using mFullNodeList
		// which is probably what we want, although this can do all and more that you can
		// do with mFullNodeList.
		// for (Node n : trl.mNodeList) {  
		for (Node n : mFullNodeList) {  
			String nodeAttVal = nat.valueString(n);	
			if (nodeAttVal != null && nodeAttVal.equalsIgnoreCase(attvalue)) {
				newNodeList.add(n);
				++count;
			}
		}
		// Check if any nodes in set:
		if (newNodeList.size() <= 0) {
			Util.msg("No nodes in set, filter not applied"); // why not? Need to handle empty list
			ps.clearFilter();
			// MFK this goes to no filter, perhaps it should revert to previous filter
			// For now the pstate says no filter, but the trial may still have a filtered list.
		} else {
			// Filters on:
			mNodeList = newNodeList;
			setNodeIndex(0);
			Util.toast("Filtering on attribute " + nat.name() + " = " + attvalue + "\n"
					+ count + " nodes in set");
		}
	}
	
	//*** Sorting Stuff: **************************************************************************
	// lots of not used code here, housekeep..
	/*
	 * restoreScoringState()
	 * If Bundle is null then we have started from scratch, otherwise it's a restart with
	 * relevant details (that aren't in the db) in the Bundle.
	 */
	public boolean restoreScoringState(Bundle savedInstanceState) {
		boolean bres = pstate.restore(this);
		applyFilter(pstate);
		pstate.sortList(null, this);
		return bres;
	}
	public void saveScoringState() {
		pstate.save(getId());
	}

	/*
	 * localNodeComparison()false
	 * Comparison value where at least one of the nodes is local (which is assumed).
	 * Local nodes are always placed at the front, in creation order.
	 * NB Some code assumes this will not return 0 unless a == b.
	 */
	private int localNodeComparison(Node a, Node b) {
		if (a.isLocal()) {
			if (b.isLocal()) {
				if (a.localNumber() < b.localNumber()) return -1;
				if (a.localNumber() > b.localNumber()) return 1;
				return 0;
			}
			else return -1;
		}// else if (b.isLocal())  - assuming one of the nodes is local
		return 1;
	}	

	class NodeComparator implements Comparator<Node> {
		private SortType mOrdering;
		private int mReverse;

		/*
		 * Constructor:
		 * NB, reverse means the rows or cols walked along are reversed
		 * (but not the other dimension).
		 */
		NodeComparator(SortType ordering, boolean reverse) {
			mOrdering = ordering;
			mReverse = reverse ? -1 : 1;
		}

		@Override
		public int compare(Node a, Node b) {
			// Always put new nodes (locally created) at the front:
			if (a.isLocal() || b.isLocal())
				return localNodeComparison(a, b);
			
			switch (mOrdering) {
			default:
			case SORT_ROW:
				if (a.mRow < b.mRow)
					return -1;
				if (a.mRow > b.mRow)
					return 1;
				if (a.mCol == b.mCol)
					return 0;
				if (a.mCol < b.mCol)
					return mReverse * -1;
				return mReverse * 1;
			case SORT_COLUMN:
				if (a.mCol < b.mCol)
					return -1;
				if (a.mCol > b.mCol)
					return 1;
				if (a.mRow == b.mRow)
					return 0;
				if (a.mRow < b.mRow)
					return mReverse * -1;
				return mReverse * 1;
			case SORT_COLUMN_SERPENTINE:
				if (a.mCol < b.mCol)
					return -1;
				if (a.mCol > b.mCol)
					return 1;
				if (a.mRow == b.mRow)
					return 0;
				if (a.mRow < b.mRow)
					return mReverse * (a.mCol % 2 == 1 ? -1 : 1);
				return mReverse * (a.mCol % 2 == 1 ? 1 : -1);
			case SORT_ROW_SERPENTINE:
				if (a.mRow < b.mRow)
					return -1;
				if (a.mRow > b.mRow)
					return 1;
				if (a.mCol == b.mCol)
					return 0;
				if (a.mCol < b.mCol)
					return mReverse * (a.mRow % 2 == 1 ? -1 : 1);
				return mReverse * (a.mRow % 2 == 1 ? 1 : -1);
			}
		}
	}
	class NodeAttributeComparator implements Comparator<Node> {
		private NodeAttribute mAtt;
		private int mReverse;

		/*
		 * Constructor:
		 */
		NodeAttributeComparator(NodeAttribute att, boolean reverse) {
			mAtt = att;
			mReverse = reverse ? -1 : 1;
		}

		@Override
		public int compare(Node a, Node b) {
			// Always put new nodes (locally created) at the front:
			if (a.isLocal() || b.isLocal())
				return localNodeComparison(a, b);

			// MFK VERY INEFFICIENT - should get all the attribute values
			// once somehow. Could use working field in Node, but this a bit undesirable..
			// Also not checked yet!
			
			// Get the node attribute values:
			Integer aval = a.getAttributeInt(mAtt.mName);
			Integer bval = b.getAttributeInt(mAtt.mName);
			
			if (aval == null && bval == null) return 0;
			if (aval == null) return mReverse;
			if (bval == null) return mReverse * -1;
			
			return aval.compareTo(bval) * mReverse;
		}
	}
	class NodeAttributeComparator2 implements Comparator<Node> {
		private NodeAttribute mAtt1;
		private NodeAttribute mAtt2;
		private SortDirection mDir1, mDir2;
		private int mSerpentine = 0;  // 0 for not serp, 1 for UpThenDown, -1 for DownThenUp

		/*
		 * Constructor:
		 * Could work out here if we are doing serpentine. Which we only allow when
		 * the relevant index is integer - and then we set direction according to odd
		 * or even.
		 * Specifically:
		 * a1 must be integer, with 1 (odd) meaning ascend in a2.
		 * Maybe better ensure in here that mDir2 (or mDir1) is not null.
		 */
		NodeAttributeComparator2(NodeAttribute a1, SortDirection d1, NodeAttribute a2, SortDirection d2) {
			if (d2 == SortDirection.ALT_DOWN_UP)
			mAtt1 = a1;
			mAtt2 = a2;
			mDir1 = d1;
			mDir2 = d2;
		}
/*
 * Note not doing serpentine
 */
		@Override
		public int compare(Node a, Node b) {
			// Always put new nodes (locally created) at the front:
			if (a.isLocal() || b.isLocal())
				return localNodeComparison(a, b);

			// MFK VERY INEFFICIENT - should get all the attribute values
			// once somehow. Could use working field in Node, but this a bit undesirable..			
			int comp1 = mAtt1.compare(a, b) * ((mDir1 == SortDirection.DESC) ? -1 : 1);
			if (comp1 != 0 || mAtt2 == null)
				return comp1;
			else {
// we really need to cache values, as these are being got from the db (in even() and compare()
// Could cache them in a hash object, on object addresses?, in this class instance.
// or perhaps in the node objects. Or perhaps do a single db access to retrieve all the values
// and build a hash. We know that every item must have its value retrieved at least once.	
// hash could be built in the above constructor, hashing from node id to value.				
				int comp2 = mAtt2.compare(a, b);
				switch (mDir2) {
				case ALT_UP_DOWN:
					return mAtt1.even(a) ? comp2 : -comp2;   // Note we could use a or b here as same for att1
				case ASC:
					return comp2;
				case ALT_DOWN_UP:
					return mAtt1.even(a) ? -comp2 : comp2;
				case DESC:
					return -comp2;
				default:
					return 0;
				}
				//return mAtt2.compare(a, b) * ((mDir2 == SortDirection.DESC) ? -1 : 1);
			}
		}
	}
	
	// version with cached lookups
	class NodeAttributeComparator3 implements Comparator<Node> {
		private NodeAttribute mAtt1;
		private NodeAttribute mAtt2;
		private SortDirection mDir1, mDir2;
		private int mSerpentine = 0;  // 0 for not serp, 1 for UpThenDown, -1 for DownThenUp

		/*
		 * Constructor:
		 * Could work out here if we are doing serpentine. Which we only allow when
		 * the relevant index is integer - and then we set direction according to odd
		 * or even.
		 * Specifically:
		 * a1 must be integer, with 1 (odd) meaning ascend in a2.
		 * Maybe better ensure in here that mDir2 (or mDir1) is not null.
		 */
		NodeAttributeComparator3(NodeAttribute a1, SortDirection d1, NodeAttribute a2, SortDirection d2) {
			if (d2 == SortDirection.ALT_DOWN_UP)
			mAtt1 = a1;
			mAtt2 = a2;
			mDir1 = d1;
			mDir2 = d2;
		}
/*
 * Note not doing serpentine
 */
		@Override
		public int compare(Node a, Node b) {
			// Always put new nodes (locally created) at the front:
			if (a.isLocal() || b.isLocal())
				return localNodeComparison(a, b);

			// MFK VERY INEFFICIENT - should get all the attribute values
			// once somehow. Could use working field in Node, but this a bit undesirable..			
			int comp1 = mAtt1.compare(a, b) * ((mDir1 == SortDirection.DESC) ? -1 : 1);
			if (comp1 != 0 || mAtt2 == null)
				return comp1;
			else {
// we really need to cache values, as these are being got from the db (in even() and compare()
// Could cache them in a hash object, on object addresses?, in this class instance.
// or perhaps in the node objects. Or perhaps do a single db access to retrieve all the values
// and build a hash. We know that every item must have its (att1) value retrieved at least once.	
// hash could be built in the above constructor, hashing from node id to value.				
				int comp2 = mAtt2.compare(a, b);
				switch (mDir2) {
				case ALT_UP_DOWN:
					return mAtt1.even(a) ? comp2 : -comp2;   // Note we could use a or b here as same for att1
				case ASC:
					return comp2;
				case ALT_DOWN_UP:
					return mAtt1.even(a) ? -comp2 : comp2;
				case DESC:
					return -comp2;
				default:
					return 0;
				}
				//return mAtt2.compare(a, b) * ((mDir2 == SortDirection.DESC) ? -1 : 1);
			}
		}
	}
	public void sortNodes(SortType ordering, boolean reverse) {
		Collections.sort(mNodeList, new NodeComparator(ordering, reverse));
		setNodeIndex(0); // after sorting, start at first
	}
	
	
//	// Sort on attribute value, att is assumed to have datatype integer.
//	public void sortNodes(NodeAttribute att, boolean reverse) {
//		if (att.datatype() != T_INTEGER)
//			return;  // Do nothing if not integer type
//		Collections.sort(mNodeList, new NodeAttributeComparator(att, reverse));
//		setNodeIndex(0); // after sorting, start at first
//	}

	public void sortNodes(NodeAttribute a1, SortDirection d1, NodeAttribute a2, SortDirection d2) {
		Collections.sort(mNodeList, new NodeAttributeComparator2(a1, d1, a2, d2));
		setNodeIndex(0); // after sorting, start at first
	}
	
	//##################################################################################################
	class AttSortThing {
		Object a1val;
		Object a2val;
		Node node;
		boolean even;
		AttSortThing(Object v1, Object v2, Node n, boolean ev) {
			a1val = v1;
			a2val = v2;
			node = n;
			even = ev;
		}
	}
	class NodePairComparator implements Comparator<AttSortThing> {
		/*
		 *  NB this should always give a non zero order to two different nodes.
		 * This is achieved by falling back to comparing node ids when
		 * the nodes are otherwise equal.
		 * 
		 * we really need to cache values, as these are being got from the db (in even() and compare()
		 * Could cache them in a hash object, on object addresses?, in this class instance.
		 * or perhaps in the node objects. Or perhaps do a single db access to retrieve all the values
		 * and build a hash. We know that every item must have its (att1) value retrieved at least once.	
		 * hash could be built in the above constructor, hashing from node id to value.		
		 */
		private Datatype mDt1;
		private Datatype mDt2;
		private SortDirection mDir1;
		private SortDirection mDir2;
		
		NodePairComparator(SortDirection d1, Datatype dt1, SortDirection d2, Datatype dt2) {
			// NB d2 and dt2 should be null if no second sort attribute
			mDir1 = d1;
			mDt1 = dt1;
			if (d2 != null && dt2 != null) {
				mDir2 = d2;
				mDt2 = dt2;
			}
		}
		@Override
		public int compare(AttSortThing ast1, AttSortThing ast2) {
			Node a = ast1.node;
			Node b = ast2.node;
			Object a1v1 = ast1.a1val;
			Object a1v2 = ast1.a2val;
			Object b1v1 = ast2.a1val;
			Object b1v2 = ast2.a2val;
			
			// Always put new nodes (locally created) at the front:
			if (a.isLocal() || b.isLocal())
				return localNodeComparison(a, b);

			int idOrder = Util.compare(a.getId(), b.getId());
			int comp1 = mDt1.compare(a1v1, b1v1) * ((mDir1 == SortDirection.DESC) ? -1 : 1);
			if (comp1 != 0 || mDir2 == null)
				return comp1 == 0 ? idOrder : comp1;
			else {
				int comp2 = mDt2.compare(a1v2, b1v2);
				if (comp2 == 0) return idOrder;
				switch (mDir2) {
				case ALT_UP_DOWN:
					return ast1.even ? -comp2 : comp2;   // Note we could use a or b here as same for att1
				case ASC:
					return comp2;
				case ALT_DOWN_UP:
					return ast1.even ? comp2 : -comp2;
				case DESC:
					return -comp2;
				default:
					return idOrder;
				}
			}
		}
	}
	public Result sortNodes3(NodeProperty a1, SortDirection d1, NodeProperty a2, SortDirection d2) {
		// Inserting into a tree to do the sorting.
		// Could use list sort instead. Make list of NodePairComparator and sort. Should compare performance.
		// NB tree skips duplicates (but this OK with above comparator).
		if (a1 == null || d1 == null)
			return new Result(false, "No first attribute for sorting");
		TreeSet<AttSortThing> ts = new TreeSet<AttSortThing>(
				new NodePairComparator(d1, a1.datatype(), d2, a2 == null ? null : a2.datatype()));
		boolean evenable = (a2 != null && a2 != null && (d2 == SortDirection.ALT_DOWN_UP || d2 == SortDirection.ALT_UP_DOWN));
		for (Node nd : mNodeList) {
			Object a2val = a2 == null ? null : a2.valueObject(nd);
			AttSortThing ast = new AttSortThing(a1.valueObject(nd), a2val, nd, evenable ? a1.even(nd) : false);
			ts.add(ast);
		}
		ArrayList<Node> newlist = new ArrayList<Node>();
		for (AttSortThing ast : ts) {
			newlist.add(ast.node);
		}
		mNodeList = newlist;
		setNodeIndex(0); // after sorting, start at first
		return new Result();
	}
	//##################################################################################################

//	class NodePropertyPairComparator implements Comparator<Node> {
//		private NodeProperty mAtt;
//		private int mReverse;
//
//		/*
//		 * Constructor:
//		 */
//		NodePropertyPairComparator(NodeProperty p1, int direction1, NodeProperty p2, int direction2) {
//			mAtt = att;
//			mReverse = reverse ? -1 : 1;
//		}
//
//		@Override
//		public int compare(Node a, Node b) {
//			// Always put new nodes (locally created) at the front:
//			if (a.isLocal() || b.isLocal())
//				return localNodeComparison(a, b);
//
//			// MFK VERY INEFFICIENT - should get all the attribute values
//			// once somehow. Could use working field in Node, but this a bit undesirable..
//			// Also not checked yet!
//			
//			// Get the node attribute values:
//			Integer aval = a.getAttributeInt(mAtt.mName);
//			Integer bval = b.getAttributeInt(mAtt.mName);
//			
//			if (aval == null && bval == null) return 0;
//			if (aval == null) return mReverse;
//			if (bval == null) return mReverse * -1;
//			
//			return aval.compareTo(bval) * mReverse;
//		}
//	}
//	// Sort on two attribute value, att is assumed to have datatype integer. Should these be NodeProperty?
//	public void sortNodes(NodeAttribute att1, int direction1, NodeAttribute att2, int direction2, boolean reverse) {
//		if (att1.datatype() != T_INTEGER || att2.datatype() != T_INTEGER)
//			return;  // Do nothing if not integer type
//		Collections.sort(mNodeList, new NodeAttributeComparator(att, reverse));
//		setNodeIndex(0); // after sorting, start at first
//	}
	
	/*** END SORT STUFF **************************************************************************/
	
	/*
	 * addScoreSets()
	 * Create score sets according to repCounts. This array should have the same size as the
	 * current mTraitList, and gives the number of replicates to create for in a score set of
	 * the corresponding index. NB A negative count must be used to indicate that no score set
	 * should be created for the corresponding trait. A create count of 0 is used to indicate
	 * that a score set should be created that allows an arbitrary number of samples, which
	 * will be dynamically create while scoring.
	 * 
	 * The created traitInstances are added to the database.
	 * MFK - perhaps take the DB stuff out of here into a writeDB func in TraitInstance.
	 * NB When adding we preserve grouping of reps within instances together.
	 */
	public String addScoreSets(int[] repCounts) {
		// or should this be a trait method? maybe not as trial specific..

		// We assume the repCounts reflects the current mTraitList in this trial, order and all..
		if (repCounts.length != mTraitList.size())
			return "Error in adding trait instances";

		int dayNum = Util.JapaneseDayNumber(); // get today's date
		for (int tind = 0; tind < repCounts.length; ++tind) {
			int count = repCounts[tind];
			if (count < 0)
				continue;

			Trait trt = mTraitList.get(tind);

			/*
			 * Find max seqNum, as we will need to use the next one, if this is the first, it gets number 1.
			 * Note that if score sets have been deleted, the numbers may be reused. This is not ideal,
			 * but at the moment we have no record of deleted score sets.
			 */
			String whereCond = String.format(Locale.US, "%s=%d and %s=%d", TI_TRIAL_ID, getId(), TI_TRAIT_ID, trt.getId());
			Cursor ccr = null;
			int instanceNum = 0;
			try {		
				ccr = g_db().query(TABLE_TRAIT_INSTANCE, new String[] { "max(" + TI_SEQNUM + ")" }, whereCond, null, null, null, null);
				if (ccr.getCount() == 1) {
					ccr.moveToFirst();
					instanceNum = ccr.getInt(0);
				}
			} finally { if (ccr != null) ccr.close(); }
			++instanceNum;

			/*
			 * We need to create one RepSet, and a TraitInstance for each rep.
			 * Note that if count is zero, we create a single ti with sample number zero,
			 * this is a "dynamic" scoreset.
			 */
			RepSet rs = new RepSet(trt, dayNum, instanceNum, count);
//			for (int samp = (count == 0 ? 0 : 1); samp <= count; ++samp) {
//				TraitInstance ti = new TraitInstance(trt, dayNum, instanceNum, samp, rs); // db add in here
//				rs.add(ti);
//			}
			mScoreSets.add(rs);
		}
		return null;
	}

	/*
	 * GetTraitById()
	 */
	private Trait GetTraitById(long id) {
		for (int i = 0; i < mTraitList.size(); ++i) {
			if (mTraitList.get(i).getId() == id)
				return mTraitList.get(i);
		}
		return null;
	}

	/*
	 * initScoreSetList()
	 * Fill the RepSet list from the database, with all the trait instances
	 * currently existing for this trial.
	 * NB List must be constructed with specific order, as this is assumed elsewhere.
	 * NB mTraitList must have been set prior to a call to this function.
	 */
	private void initScoreSetList() {
		//String whereCond = String.format("%s=%d and %s >= 0", TI_TRIAL_ID, getID(), TI_SAMPNUM);  // hack alert > 0 to avoid attribute traits
		String whereCond = String.format(Locale.US, "%s=%d", TI_TRIAL_ID, getId());  // hack alert > 0 to avoid attribute traits
		Cursor ccr = null;
		try {		
			ccr = g_db().query(TABLE_TRAIT_INSTANCE,
					new String[] { TI_ID, TI_TRAIT_ID, TI_DAYCREATED, TI_SEQNUM, TI_SAMPNUM },
					whereCond, null, null, null,
					TI_DAYCREATED + " DESC, " + TI_TRAIT_ID + " ASC, " + TI_SEQNUM + " ASC, " + TI_SAMPNUM	+ " ASC");
			mScoreSets.clear();
			RepSet rs = null;
			if (ccr.moveToFirst()) {
				long lastTid = 0;
				long lastDayCreated = 0;
				long lastSeq = 0;
				do {
					//for (int j = 0; j < numExists; ++j) {
					long tid = ccr.getLong(0);
					int traitId = ccr.getInt(1);
					Trait trait = GetTraitById(traitId);
					int createDate = ccr.getInt(2);
					int seqNum = ccr.getInt(3);
					int sample = ccr.getInt(4);
					// Check if this is a new Repset: (MFK traitId and seqNum should be sufficient now)
					if (lastTid != traitId || lastDayCreated != createDate || lastSeq != seqNum) {
						if (rs != null) // added completed RepSet to list
							mScoreSets.add(rs);
						rs = new RepSet(trait, createDate, seqNum);
	
						lastTid = traitId;
						lastDayCreated = createDate;
						lastSeq = seqNum;
					} else {
						if (rs == null)
							rs = new RepSet(trait, createDate, seqNum);
					}
					rs.add(new TraitInstance(tid, trait, createDate, seqNum, sample, rs));
				} while (ccr.moveToNext());
			}
			if (rs != null) // add last completed RepSet to list
				mScoreSets.add(rs);
		} finally { if (ccr != null) ccr.close(); }
		return;
	}

	/*
	 * GetRepSetNames()
	 * Returns array of trait instance names. One for each repset.
	 */
	public String[] GetRepSetNames() {
		String[] names = new String[mScoreSets.size()];
		for (int i = 0; i < mScoreSets.size(); ++i)
			names[i] = mScoreSets.get(i).getDescription();
		return names;
	}
	public RepSet[] getRepSetsArray() {
		return mScoreSets.toArray(new RepSet[mScoreSets.size()]);
	}
	
	/*
	 * getRepSetsArrayList()
	 * Returns copy of score set list.
	 */
	public ArrayList<RepSet> getRepSetsArrayList() {
		return new ArrayList<RepSet>(mScoreSets);
	}

	/*
	 * GetTraitInstanceRepSet()
	 * Returns trait instance at given index in list, NB No Checking list or index valid!
	 */
	public RepSet getTraitInstanceRepSet(int index) {
		return mScoreSets.get(index);
	}

	/*
	 * getTraitList()
	 * Returns mTraitList as array.
	 */
	public Trait[] getTraitList() {
		return mTraitList.toArray(new Trait[mTraitList.size()]);
	}

	public String getName() {
		return mName;
	}

	public void setName(String name) {
		mName = name;
	}

	/*
	 * DeleteTrait()
	 * Delete trait from this trial.
	 * If the trait is only in this trial, then the trait itself is also deleted. System traits are not deleted.
	 * Error is returned if trait has trait instances, or other problem occurs.
	 * Returns 0 on (apparent) success
	 * MFK only used by trait dialog which is no longer available ATTOW, may need fixing before
	 * using again as some things have changed.
	 */
	public Result DeleteTrait(Trait trt) {
		// Check no trait instances of this trait in this trial:
		String qry = String.format(Locale.US, "select count(*) from %s where %s = %d and %s = %d", TABLE_TRAIT_INSTANCE, TI_TRIAL_ID, this.getId(),
				TI_TRAIT_ID, trt.getId());
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(qry, null);
			ccr.moveToFirst();
			int count = ccr.getInt(0);
			if (count > 0)
				return new Result(false, "Cannot delete trait " + trt.getCaption() + " as there are existing TraitInstances");
		} finally { if (ccr != null) ccr.close(); }

		// Delete from this trial (i.e. trialTrait table):
		String trialTraitClause = String.format(Locale.US, "%s = %d and %s = %d", TT_TRIALID, getId(), TT_TRAITID, trt.getId());
		if (1 != g_db().delete(TABLE_TRIALTRAIT, trialTraitClause, null)) {
			return new Result(false, "Warning, delete from trialTrait may not have worked");
		}

		if (!trt.isSystemTrait()) {
			if (1 != g_db().delete(TABLE_TRAIT, TR_ID + " = " + trt.getId(), null)) {
				return new Result(false, "Warning, delete from trait may not have worked");
			}
		}

		// Delete from the trait list. What about replist, trait instances, data present?
		mTraitList = Trait.getTraitListFromDB(this);
		//SetTraitListFromDB(); // Refresh mTraitList

		return new Result();
	}

	/*
	 *  DeleteRepSet()
	 *  Deletes TraitInstance of repset from db, and the repset and tis from the trial lists.
	 */
	void DeleteRepSet(RepSet rs) {
		for (TraitInstance ti : rs.getTraitInstanceList()) {
			rs.getTrait().DeleteTraitInstance(ti);
		}
		mScoreSets.remove(rs); // Delete from list
	}

	/*
	 * ImportTraitFile()
	 * Imports traits from a file.
	 * NB Only some trait types are supported.
	 * Trait are added as local (trial specific) traits.
	 */
	public Result ImportTraitFile(String fname) {
		File tplate = new File(fname);
		// Now open the file and display first line:
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(tplate));
			String line;
			line = br.readLine(); // read header line:

			// find indicies of columns with headers "row" and "column"
			String[] hdrs = line.split("\\s*,\\s*");

			final int CAPTION = 0;
			final int DESCRIPTION = 1;
			final int TYPE = 2;
			final int UNIT = 3;
			final int ID = 4;
			final int MIN = 5;
			final int MAX = 6;

			// MFK NB id not used.
			String[] attNames = { "caption", "description", "type", "unit", "id", "min", "max" };
			int[] indexes = { -1, -1, -1, -1, -1, -1, -1 };

			// See what headers match attNames:
			for (int i = 0; i < hdrs.length; ++i) {
				for (int j = 0; j < attNames.length; ++j) {
					if (hdrs[i].toLowerCase(Locale.US).contains(attNames[j])) {
						if (indexes[j] >= 0) {
							return new Result(false, "Multiple column headers containing " + attNames[j] + ", aborting trait import");
						}
						indexes[j] = i;
					}
				}
			}
			// Check we at least have a caption, type, and description:
			for (int x : new int[] { CAPTION, DESCRIPTION, TYPE }) {
				if (indexes[x] < 0) {
					return new Result(false, "No column header found containing " + attNames[x] + ", aborting trait import");
				}
			}

			// Now process the data lines:
			while ((line = br.readLine()) != null) {
				//use Newlocaltrait - make it add defaults for nulls?
				//Trait trt = new Trait(); // used as convenient declaration of all trait fields with relevant defaults.
				// do something with the trait:
				String[] fields = line.split("\\s*,\\s*");
				String caption = fields[indexes[CAPTION]].replaceAll("^\"|\"$", "");
				String description = fields[indexes[DESCRIPTION]].replaceAll("^\"|\"$", "");
				Datatype iType;
				String ftype = fields[indexes[TYPE]].replaceAll("^\"|\"$", "").toLowerCase(Locale.US);
				if (ftype.equals("integer"))
					iType = T_INTEGER;
				else if (ftype.equals("decimal"))
					iType = T_DECIMAL;
				else if (ftype.equals("string") || ftype.equals("text"))
					iType = T_STRING;
				else
					return new Result(false, "Unsupported type : " + ftype);

				String tid = (indexes[ID] >= 0) ? fields[indexes[ID]].replaceAll("^\"|\"$", "") : "";  // MFK tid not used
				String unit = (indexes[UNIT] >= 0) ? fields[indexes[UNIT]].replaceAll("^\"|\"$", "") : "";
				Double dmin = null, dmax = null;
				if (indexes[MIN] >= 0) {
					String fmin = fields[indexes[MIN]].replaceAll("^\"|\"$", "");
					if (Util.isNumeric(fmin))
						dmin = Double.parseDouble(fmin);
					else if (iType != T_STRING)
						Util.msg("Warning. Min unparseable for trait " + caption + " using default instead");
				}
				if (indexes[MAX] >= 0) {
					String fmax = fields[indexes[MAX]].replaceAll("^\"|\"$", "");
					if (Util.isNumeric(fmax))
						dmax = Double.parseDouble(fmax);
					else
						Util.msg("Warning. Max unparseable for trait " + caption + " using default instead");
				}
				Result res = AddAdHocTrait(caption, description, iType, tid, unit, dmin, dmax);
				if (res.bad())
					return res;
			}
		} catch (IOException e) {
			return new Result(false, "Cannot read from file " + fname);
		} finally {
			if (br != null)
				try { br.close(); } catch (IOException e) {}
		}
		
		return new Result();
	}

	
	/*
	 * processJSONAttributes()
	 * Clear any existing attributes, and replace with those specified in the jsonArray.
	 * NB, this is attribute definitions only not values - although any existing values are cleared.
	 * Each item in the array is an object describing one attribute, with fields: id, name,
	 * datatype, and func.
	 */
	private void processJSONAttributes(JSONArray attributes) throws JSONException {
		clearAttributes();    // First clear all existing attributes.
		int numAtts = attributes.length();
		for (int i = 0; i < numAtts; i++) {
			JSONObject att = attributes.getJSONObject(i);
			int id = att.getInt(TUA_ID);
			String attname = att.getString(TUA_NAME);
			Datatype datatype = Datatype.fromInt(att.getInt(TUA_DATATYPE));
			int func = att.getInt(TUA_FUNC);
			if (!AttributePresent(attname)) {     // MFK, not clear this is necessary.
				createAttribute(id, attname, datatype, func);
			}
		}
	}
	
	/*
	 *  processJSONTraits()
	 *  Process the traits in the JSONArray. Each item in the array should be an object describing
	 *  one trait. The JSON format might vary between trait types, so we hand this over to Trait to sort out.
	 *  MFK note new traits not being added to mTraitList, which may not matter..
	 */
	private Result processJSONTraits(JSONArray jtraits) throws JSONException {
		Result res;
		for (int i = 0; i < jtraits.length(); i++) {
			res = Trait.addOrUpdateTraitFromJSON(this, jtraits.getJSONObject(i)); // NB will not create trait if already present.
			if (res.bad()) {
				return res;
			}
		}
		return new Result();
	}
	
	/*
	 * processJSONTrialPropertyValues()
	 */
	private void processJSONTrialPropertyValues(JSONObject jtrial) throws JSONException {
		// Get optional attributes:
		if (jtrial.has(JTRL_TRIAL_PROPERTIES)) {
			JSONObject jatts = jtrial.getJSONObject(JTRL_TRIAL_PROPERTIES);
		    Iterator<String> iter = jatts.keys();
		    while (iter.hasNext()) {
		        String key = iter.next();
		        try {
		            Object value = jatts.get(key);
		            if (value instanceof String) {
		            	// save key value
		            	saveTrialPropertyValue(key, (String)value);
		            }
		        } catch (JSONException e) {
		            // Something went wrong!
		        }
		    }
		}
	}

	/*
	 * processJSONNodeAttributeValues()
	 */
	private void processJSONNodeAttributeValues(JSONObject jnode, Node node) throws JSONException {
		// Get optional attributes:
		if (jnode.has(JNODE_ATTVALS)) {
			JSONObject jnatts = jnode.getJSONObject(JNODE_ATTVALS);
			for (NodeAttribute att : mAttributeList) {
				if (!jnatts.isNull(att.name()))
					node.setAttributeValue(att, jnatts.getString(att.name()));
			}
		}
	}

	// Get GPS location if provided:
	private Location getJSONNodeLocation(JSONObject jtu) throws JSONException {
		Location loc = null;
		if (jtu.has(JNODE_LOCATION)) {
			JSONArray jloc = jtu.getJSONArray(JNODE_LOCATION);
			double latitude = jloc.getDouble(0);
			double longitude = jloc.getDouble(1);
			loc = Util.MakeLocation(latitude, longitude);
		}
		return loc;
	}
	
	/*
	 * processJSONNodes()
	 * Sub func of FillOrUpdateJSONTrial() see parameters there.
	 */
	private Result processJSONNodes(JSONArray nodes, boolean update, ActMain.ShowProgress prog) throws JSONException {
		int numNodes = nodes.length();
		for (int i = 0; i < numNodes; i++) {
			JSONObject jtu = nodes.getJSONObject(i);
			int row = jtu.getInt(JND_ROW);
			int col = jtu.getInt(JND_COL);
			int nodeId = jtu.getInt(JND_ID);
			String desc = jtu.getString(JND_DESC);
			String barcode = jtu.getString(JND_BARCODE);
			Location loc = getJSONNodeLocation(jtu);  // Get GPS location if provided
			
			Node node = null;
			if (update) {
				// MFK - for efficiency, we could have a hash of nodes on id
				/*
				 * Find the node and update the fields if it already exists.
				 * If not the node will remain null and it will be created below.
				 * Note if we do allow add, then should we detect delete as well?
				 */
				if ((node = getNodeById(nodeId)) != null) {
					node.mDescription = desc;
					node.mBarcode = barcode;
					node.mLocation = loc;
					node.mRow = row;
					node.mCol = col;
					node.updateDB();
				}
			}
			/*
			 * Add new node if we are not in an update, OR, we are,
			 * but no matching node was found.
			 */
			if (node == null) {
				Result res = addNode(nodeId, row, col, desc, barcode, loc);  
				if (res.bad())
					return res;
				node = (Node) res.obj();
			}
			processJSONNodeAttributeValues(jtu, node);
			
			// update async task occasionally:
			if (i % 100 == 0)
				prog.showProgress("Processed " + i + " of " + numNodes + " nodes");
		}
		return new Result();
	}

	/*
	 * FillOrUpdateJSONTrial()
	 * Sets a trial according to the passed JSON. This may be called with a newly created
	 * trial (in which case we assume the nodes are yet to be created), or with an
	 * existing trial (in which case this is treated as an update). 
	 * NB we could probably move this ambiguity through to an AddOrUpdateNode() function.
	 * 
	 * What can be updated?
	 * nodes:
	 *   for existing ones, description, barcode, and location can be updated.
	 *   Alternatively new ones (identified by not matching any existing row/col) can be added.
	 *   NB node ID, from the server, is NOT updated, since we may have references to it (eg datum).
	 * Attributes:
	 *   All existing attributes and their values are removed, and new ones, and
	 *   their values, are created from the json.
	 * Traits:
	 * 	The updatable fields are: caption, description, uploadURL, .
	 * MFK - should we be able to add new categories to cat traits, or pictures?
	 *  Also trait type specific handling is invoked.
	 */
	public Result fillOrUpdateJSONTrial(JSONObject json, boolean update, ActMain.ShowProgress prog) throws JSONException {
		Result res;
		
		// Update top level trial fields:
		if (!update) {
			// If we update the token, exports of existing score sets will be treated like new ones on the server.
			mAdhocURL = json.getString(JTRL_ADHOC_URL);
			mUploadURL = json.getString(JTRL_UPLOAD_URL);
			mServerToken = json.getString(JTRL_SERVER_TOKEN);
		}
		mYear = json.getString(JTRL_YEAR);
		mSite = json.getString(JTRL_SITE);
		mName = json.getString(JTRL_NAME);
		mAcronym = json.getString(JTRL_ACRONYM);
		updateDB();
		
		// The attribute information should be in the json object as an array (with known name):
		processJSONAttributes(json.getJSONArray(JTRL_NODE_ATTRIBUTES));
		
		// Trial properties:
		processJSONTrialPropertyValues(json);
		
		// Nodes:
		res = processJSONNodes(json.getJSONArray(JTRL_NODES_ARRAY), update, prog);
		if (res.bad())
			return res;
		
		// Traits:
		return processJSONTraits(json.getJSONArray("traits"));
	}

	/*
	 * UpdateFromServer()
	 * Starts async process to update trial.
	 * Attow: To update the trial we do a full get trial from the server
	 * (i.e. the same as we do when first downloading the trial).
	 * And then update the bits that we are supporting update for,
	 * which at the moment is just the attributes set and attribute values.
	 * There may be some code duplication from the get full trial operation.
	 * i.e. from ImportJSONTrial()  so keep these in sync and refactor 
	 * appropriately when time permits.
	 * 
	 * NB Probably should be merged with MainActivity.GetTrialFromServer(),
	 * the async tasks are almost identical.
	 * 
	 * MFK I think we can now update name (any reason why not?), but if we
	 * do this, lists may be out of date..
	 */
	public void UpdateFromServer() {
		//if (Globals.setBusy("Updating Trial " + getName())) {
			new UpdateTrialFromServerAsync().execute(new Trial [] { this });
		//}
	}
	class UpdateTrialFromServerAsync extends AsyncTask<Trial, String, Void> implements ActMain.ShowProgress {
		private Exception exception;
		String mErrMsg;

		@Override
		protected Void doInBackground(Trial... trials) {
			try {
				Trial trl = trials[0];
				// We have the URL already, but we need to add password and androidId parameters
				Result res = Server.getJSON(trl.getUploadURL());
				if (res.bad()) {
					mErrMsg = res.errMsg();
					return null;
				}
				JSONObject job = (JSONObject) res.obj();
				showProgress("Updating trial " + trl.getName());
				trl.fillOrUpdateJSONTrial(job, true, this);
			} catch (Exception e) {
				this.exception = e;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			ActMain.stopBusy();  // should be in util?
			if (exception == null && mErrMsg == null) {
				Util.msg("Trial updated");
				return;
			}
			if (exception != null) {
				Util.exceptionHandler(exception, "UpdateTrialFromServerAsync:onPostExecute");
			} else if (mErrMsg != null) {
				Util.msg("Error: " + mErrMsg);
			}
		}
		
		@Override
		protected void onProgressUpdate (String... values) {
			Util.toast(values[0]);
		}

		@Override
		public void showProgress(String msg) {
			this.publishProgress(msg);
		}
	}


	//####################################################################################
	// EXPORT CODE: ######################################################################
	//####################################################################################
	
	
	/*
	 * class ExportSelection
	 * Holds information about user selection of what to export.
	 */
	class ExportSelection {
		Trial                    trial;         // trial to export
		boolean                  exportNotes;   // whether to export node notes
		boolean                  exportNodes;   // whether to export locally created nodes
		ArrayList<TraitInstance> tis2Export;    // which trait instances to export (this could be repsets).
	}
	
	/*
	 * exportToServer()
	 * exportNotes indicates whether nodeNotes are to be exported.
	 * exportNodes indicates whether newly created nodes are to be exported.
	 * checks[i>0] indicates whether repset i-1 should be exported. Not any more!
	 */
	void exportToServer(boolean exportNotes, boolean exportNodes, boolean [] checks) {
		ExportSelection es = new ExportSelection();
		es.trial = this;
		es.exportNotes = exportNotes;
		es.exportNodes = exportNodes;
		es.tis2Export = new ArrayList<TraitInstance>();
		for (int i=0; i<checks.length; ++i) {
			if (checks[i]) {
				Trial.RepSet rs = getTraitInstanceRepSet(i);
				for (TraitInstance ti : rs.getTraitInstanceList()) {
					es.tis2Export.add(ti);
				}
			}
		}

		new ExportToServerAsync().execute(es);
	}
	
	class ExportToServerAsync extends AsyncTask<ExportSelection, String, Boolean> {
		private Exception mException;
		private Result mRes;
		private boolean mNodesToUpload = false;
		private String mNodesError = null;
		private boolean mNotesToUpload = false;
		private boolean mNotesSuccess = false;
		private String mNotesError = "";
		private String mTiError = "";
		private int mTiSuccessCount = 0;
		private int mTiFailCount = 0;
		private int mTINumToUpload = 0;
		
		protected Boolean doInBackground(ExportSelection... ess) {
			ExportSelection es = ess[0];
			Trial trial = es.trial;
			
			/*
			 * Export crash reports:
			 * 
			 */
			ExceptionHandler.uploadCrashReports();

			/*
			 * Export locally created nodes.
			 * The current process is to simply send up a list of the (locally created) ids
			 * of the locally created nodes. Server then creates nodes on the server
			 * and sends back server ids. Local database is updated to make the local nodes
			 * proper server nodes.
			 * 
			 * NB the local nodes must be exported and reified BEFORE any notes or scores
			 * for them are exported.
			 * 
			 * Need to consider what happens if, say, server gets message and creates nodes,
			 * but we don't get to create update the ids, what then?  The first line of defence
			 * is that the Server's processing of a local nodes with a given local id and server
			 * token will be idempotent. So if the client fails to record that it has been uploaded
			 * (and then updated to have a server id and so not be local anymore), and tries to
			 * upload the local nodes again, we won't end up with an extra set of duplicated nodes
			 * on the server. If, before re exporting the local nodes, the client collects scores
			 * for the local nodes, this should be fine too. What might not be fine, is if after
			 * exporting local nodes, and failing to upgrade them to server nodes, the client
			 * updates the trial. In this case it will then still have the local nodes as local,
			 * and also have the server versions of them. If it then export local nodes again,
			 * it will probably fail to upgrade them to server nodes (as id should be unique in
			 * node table).
			 * 
			 * Perhaps we should make traitInstance upload not included local nodes. Indeed if
			 * this is not the case then user could try to upload score sets without uploading
			 * local nodes first, which presumably would cause a problem on the server since
			 * it will be getting data for nodes which don't exist yet (on the server).
			 * 
			 * Note that we need to get the server ids before we can export any scores 
			 * associated with them which happen after this code segment. Since
			 * Server.UploadJSON is synchronous, this should work. By the time we
			 * get to uploading scores for the new nodes, they will have server ids.
			 * 
			 * Note there may be locally created nodes but the user has not indicated they
			 * want them exported. So we need to somehow ensure that scores, if any, for
			 * locally created nodes are NOT included in the export. Alternatively we could
			 * disallow export of scoreSets with scores in local nodes, indicating to the user
			 * that the must export the nodes, or delete the scores, or not upload the affected
			 * scoreSets. I think this last is better than only uploading scores for known
			 * nodes, which could be confusing.
			 * 
			 * This is similar to AdHoc Traits (no longer used) below.
			 */
			if (es.exportNodes) {
				mNodesToUpload = true;
				mRes = trial.localNodesAsJSON();
				if (mRes.good()) {
					JSONObject jLocalNodes = (JSONObject) mRes.obj();
					mRes = Server.uploadJSON2JSON(jLocalNodes, trial.getUploadURL());
					if (mRes.bad()) { // If an error occurred abort
						mNodesError = "Error uploading nodes:" + mRes.errMsg();
						return false;
					}
					// Get ids for the created nodes:
					JSONObject responseObj = (JSONObject)mRes.obj();
					if (!responseObj.has("nodeIds")) {
						mNodesError = "Error in response: no nodeIds element";
						return false;						
					}
					try {
						JSONArray jnodemap = responseObj.getJSONArray("nodeIds");	
						if (jnodemap.length() != trial.getNumLocalNodes()) {
							mNodesError = "Error in response: Wrong number of nodes returned";
							return false;						
						}
						
						/*
						 * Update the local nodes with the ids from server.
						 * Be aware that if this fails (eg app crashes, or device turned off),
						 * then we will have the situation where the local nodes have been
						 * created on the server, but the client doesn't know that, and hence
						 * may upload again. This should be OK, server should be idempotent
						 * for this operation. Not so clear what happens to any scores/notes
						 * collected in the meantime for the relevant nodes.
						 */
						JSONArray locNodes = jLocalNodes.getJSONArray(JTRL_NODES_ARRAY);
						for (int i = jnodemap.length() - 1; i >= 0; i--) {
							int localId = locNodes.getInt(i);
							Node n = trial.getNodeById(localId);
							if (!n.convertLocal2ServerNode(jnodemap.getInt(i))) {
								mNodesError = "Error converting local node";
								return false;
							}
						}
					} catch (Exception e) {
						mNodesError = "Error in response: " + e.getMessage();
						return false;												
					}
				}				
			}
			
			/*
			 * Export notes:
			 * NB notes for local nodes will cause failure.
			 * If the above local node upload has completed successfully, this won't
			 * occur, but we probably should alert user, and not send locals when
			 * it hasn't.
			 */
			if (es.exportNotes) {
				mNotesToUpload = true;
				mRes = trial.notesAsJSON();
				if (mRes.good()) {
					JSONObject json = (JSONObject) mRes.obj();
					if (json != null) {
						mRes = Server.uploadJSON(json, trial.getUploadURL());
						if (mRes.bad()) {
							mNotesError = mRes.errMsg();
							mNotesSuccess = false;
							return false;
						} else
							mNotesSuccess = true;
					} else {
						mNotesSuccess = true;
						mNotesToUpload = false;
					}
				} else {
					mNotesSuccess = false;
					mNotesError = "Cannot get notes from trial";
					return false;
				}
			}
	
			/*
			 * Export score sets:
			 */
			mTINumToUpload = es.tis2Export.size();
			for (TraitInstance ti : es.tis2Export) {
				Trait trt = ti.getTrait();
				try {
					//  If adhoc trait, we may need to get a server id:
					if (trt.isAdHocTrait() && !trt.hasServerID()) {
						Result res = Server.getJSON(trial.getAdhocURL(),
								// If we want to upload min and max (trait type specific), we will
								// need polymorphic handling on the server.
								new String[] {TR_CAPTION, TR_DESCRIPTION, TR_DATATYPE },
								new String[] {trt.getCaption(), trt.mDescription, Integer.toString(trt.getType().value())});
						if (res.bad()) {
							mTiError = "Error adding trait:" + res.errMsg();
							return false;
						}
						JSONObject job = (JSONObject) res.obj();
						if (job.has("traitId")) {
							int newid = Integer.parseInt(job.getString("traitId"));
							if (!trt.setServerId(newid)) {
								mTiError = "Error adding server id for trait";
								return false;
							}
						}
					}

					Result res = trt.uploadTraitInstance(ti);
					if (res.bad()) { // Concatenate to err message but carry on?
						mTiError += res.errMsg() + " ";
						++mTiFailCount;
					} else
						++mTiSuccessCount;
					publishProgress("Uploaded " + (mTiFailCount + mTiSuccessCount) + " of " + mTINumToUpload + " score sets");
				} catch (Exception e) {
					this.mException = e;
				}
			}
			
			// Return success:
			return true;
		}

		protected void onPostExecute(Boolean status) {
			ActMain.stopBusy();
			if (mException != null)
				Util.exceptionHandler(mException, "ExportToServerAsync:onPostExecute");
			else {
				String summary = status ? "" : "WARNING - Uploads aborted\n";
				if (mNodesToUpload) {
					summary += String.format("Nodes export : %s\n",
							mNodesError == null ? "success" : ("FAIL -  " + mNodesError));
				}
				if (mNotesToUpload) {
					summary += String.format("Notes export : %s\n",
							(mNotesSuccess ? "success" : "FAIL - ") + mNotesError);
				}
				if (mTINumToUpload > 0) {
					summary += "ScoreSet uploads :\n";
					summary += "" + mTiSuccessCount + "/" + mTINumToUpload + " scoreSets successfully uploaded\n";
					if (mTiFailCount > 0) {
						summary += "" + mTiFailCount + "/" + mTINumToUpload + " scoreSets failed to upload\n";
					}
					if (mTiError != null)
						summary += " " + mTiError;
				}
				Util.msg(summary);
			}
		}
		
		@Override
		protected void onProgressUpdate (String... values) {
			Util.toast(values[0]);
		}
	}


	/*
	 * ExportCSV()
	 * Writes out data to CSV file of specified name.
	 * NB Could be sped up, it access db for every datum, and no error handling.
	 * EG could retrieve all datum for the trial sorted by node_id, then traitinstance_id,
	 * but note have to cope with missing values.
	 * Could pass in booleans to make timestamp et al optional.
	 */
	public Result ExportCSV(String outfileName) {
		try {
			// Open output csv file:
			File file = new File(outfileName);
			file.createNewFile();
			FileOutputStream fOut = new FileOutputStream(file);
			OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
			// Write header row:
			myOutWriter.append("Row,Column");
						
			for (TraitInstance ti : new TraitInstanceIterator()) {
				String name = ti.getFullName();
				myOutWriter.append("," + name);
				myOutWriter.append("," + name + "_" + "timestamp");
				myOutWriter.append("," + name + "_" + "userid");
				myOutWriter.append("," + name + "_" + "latitude");
				myOutWriter.append("," + name + "_" + "longitude");
			}
			myOutWriter.append("\n");

			// Write Node data rows:
			sortNodes(Trial.SortType.SORT_ROW, false);
			for (Node tu : mNodeList) {
				myOutWriter.append(tu.mRow + "," + tu.mCol);
				for (TraitInstance ti : new TraitInstanceIterator()) {
					Datum dat = ti.getDatum();
					if (dat.hasDBValue()) {
						myOutWriter.append("," + dat.ValueAsStringForDB());
						myOutWriter.append("," + dat.getTimestamp());
						myOutWriter.append("," + dat.getUserId());
						myOutWriter.append("," + dat.getGpsLatitude());
						myOutWriter.append("," + dat.getGpsLongitude());
					} else
						myOutWriter.append(",,,,," + dat.ValueAsStringForDB());

				}
				myOutWriter.append("\n");
				gotoNextNode(); // need this to set position GetDatum references, must be a better way?
			}

			myOutWriter.close();
			fOut.close();
			return new Result();
		} catch (IOException ie) {
			Log.e(getClass().getSimpleName(), "Could not create or Open the database");
			return new Result(false, ie.getMessage());
		}
	}
	//### END EXPORT CODE ##########################################################################
	
	public String[] GetTraitCaptionList() {
		int numTraits = mTraitList.size();
		if (numTraits <= 0)
			return null;
		String[] traits = new String[numTraits];
		for (int i = 0; i < numTraits; ++i) {
			traits[i] = mTraitList.get(i).getCaption();
		}
		return traits;
	}

	/*
	 * class NodeProperty
	 * This class attempts to create a common object for node properties,
	 * where a node property is basically a function from nodes to values.
	 * 
	 * Node property can come from various sources (hence the need for a
	 * common class): attributes, node member variables, score sets, or literal
	 * values.
	 * Note this class describes a property, eg name and type, it is not the property value.
	 * NB an ID might be useful
	 */
	interface IDatum {
		boolean hasDBValue();
		boolean isNA();
		String ValueAsString(String absentVal);
		boolean navigatingAway();
		//void processTextValue(String val);
	}
	public enum NodePropertySource { ATTRIBUTE, SCORE, FIXED, LITERAL }
	static abstract class NodeProperty {
		abstract long id();                     // id should be unique within source
		abstract NodePropertySource source();
		abstract String name();                 // just use toString?
		abstract Datatype datatype();           // same set of datatypes as for scores.
		
		abstract IDatum getCurrentIDatum();
		
		/*
		 * valueString()
		 * Value of the property for given node.
		 * Note that values can be missing for a tu/property, in which case
		 * this should return null. And, for SCORE at least, the value can
		 * be NA.
		 */
		abstract String valueString(Node tu);
		
		/*
		 * value()
		 * Return the value as an Object, where the type of object will depend on the 
		 * NodeProperty datatype.  Note this not properly implemented in all sub classes ATTOW.
		 */
		abstract Object valueObject(Node nd);
		
		/*
		 * numericValue()
		 * Return value as a Double, Null return means no value present.
		 * Not this only appropriate for numeric types, other types should
		 * always return null. A better way to do this might be to have
		 * a numeric only extension of NodeProperty, as in:
		 *   static abstract class NodePropertyNumeric extends NodeProperty {
		 *     abstract Double numericValue(Node tu);
		 *   }
		 * But then we'd have to take care whenever creating a NodeProperty
		 * instance to create a NodePropertyNumeric when appropriate, eg by
		 * using factory functions rather than constructors. At the moment
		 * it's not clear this is worth it.
		 */
		abstract Double valueNumeric(Node tu);

		abstract ArrayList<?> getDistinctValues();

		/*
		 * newIntLiteralInstance()
		 * Create prop with a constant int value.
		 * Not used ATTOW
		 */
		static private NodeProperty newIntLiteralInstance(final String name, final long id, final int value) {
			return new NodeProperty() {
				@Override String name() { return name; } // any use for name? don't need?
				@Override Datatype datatype() { return T_INTEGER; }
				@Override long id() { return id; }  // could have static arraylist of ints, and id index into it.
				@Override NodePropertySource source() { return NodePropertySource.LITERAL; }
				@Override String valueString(Node tu) { return Integer.toString(value); }
				@Override Object valueObject(Node tu) { return value; }
				@Override Double valueNumeric(Node tu) {return new Double(value);}
				@Override IDatum getCurrentIDatum() { return null; }
				@Override ArrayList<?> getDistinctValues() {
					ArrayList<Integer> dvals = new ArrayList<Integer>();
					dvals.add(value);
					return dvals;
				}
			};
		}
		
		public String toString() {
			return name();
		}
		
		/*
		 * compare()
		 * Returns comparison code for the values of this NodeProperty for the specified nodes.
		 * NB missing values are placed at the end.
		 * We might need to deals with NAs if we support scores. One way to do this would be to
		 * override compare in TraitInstance, first dealing with NA, then calling back to here.
		 */
		public int compare(Node a, Node b) {
			Object oa = valueObject(a);
			Object ob = valueObject(b);
			if (oa == null)
				if (ob == null) return 0;
				else return 1;
			else if (ob == null)
				return -1;
			
			// case neither null:
			switch (datatype()) {
			case T_STRING:
				String as = (String)oa, bs = (String)ob;
				int cmp = as.compareTo(bs);
				return cmp;
				//return ((String)oa).compareTo((String)ob);
			case T_DECIMAL:
				return ((Double)oa).compareTo((Double)ob);
			case T_DATE:
			case T_INTEGER:
			case T_CATEGORICAL:
				return ((Integer)oa).compareTo((Integer)ob);
			case T_LOCATION:
			case T_NONE:
			case T_PHOTO:
			default:
				return 0;
			}
		}
		
		/*
		 * even()
		 * Return true iff integer compatible datatype and value is even for nd.
		 * 
		 */
		public boolean even(Node nd) {
			// if integer type only
			// case neither null:
			Object oa = valueObject(nd);
			if (oa == null)
				return false;
			switch (datatype()) {
			case T_DATE:
			case T_INTEGER:
			case T_CATEGORICAL:
				return ((Integer)oa) % 2 == 0;
			case T_STRING:
			case T_DECIMAL:
			case T_LOCATION:
			case T_NONE:
			case T_PHOTO:
			default:
				return false;
			}
		}
		
		/*
		 * newFixedInstance()
		 * Create prop with a constant int value.
		 */
		static private NodeProperty newFixedInstance(final Trial trial, final String name, final int fieldCode, final Datatype datatype) {
			return new NodeProperty() {
				@Override
				String name() {
					return name;
				}
				@Override
				Datatype datatype() {
					return datatype;
				}
				@Override
				long id() {
					return fieldCode;
				} // identify node field, use enum? will need if getValue func
				@Override
				NodePropertySource source() {
					return NodePropertySource.FIXED;
				}
				@Override
				String valueString(Node tu) {
					switch (fieldCode) {
					case FIELD_ROW: return String.valueOf(tu.getRow());
					case FIELD_COL: return String.valueOf(tu.getCol());
					case FIELD_BARCODE: return tu.getBarcode();
					case FIELD_LOCATION: return tu.getLocationString();
					default: return null;
					}
				}
				@Override
				Object valueObject(Node tu) {
					// TODO Auto-generated method stub
					return null;
				}
				@Override
				Double valueNumeric(Node nd) {
					switch (fieldCode) {  // Only return values for row and col:
					case FIELD_ROW: return new Double(nd.getRow());
					case FIELD_COL: return new Double(nd.getCol());
					default: return null;
					}
				}

				@Override
				ArrayList<?> getDistinctValues() {
					switch (fieldCode) {
					case FIELD_ROW:
					case FIELD_COL:
						ArrayList<Integer> vals = new ArrayList<Integer>();
						String qry = String.format(Locale.US,
								"select distinct %s from %s where %s = %d", fieldCode == FIELD_ROW ? ND_ROW : ND_COL,
								TABLE_NODE,	ND_TRIAL_ID, trial.getId());
						Cursor ccr = null;
						try {
							ccr = g_db().rawQuery(qry, null);
							if (ccr.moveToFirst())
								do vals.add(ccr.getInt(0)); while (ccr.moveToNext());
						} finally { if (ccr != null) ccr.close(); }
						return vals;
					case FIELD_BARCODE:
					case FIELD_LOCATION:
					default: return null;
					}
				}

				@Override
				IDatum getCurrentIDatum() {
					// TODO Auto-generated method stub
					return null;
				}
			};
		}
	}

	/*
	 * getNodeProperties()
	 * List of all available NodeProperties.
	 */
	public ArrayList<NodeProperty> getNodeProperties() {
		// Attributes:
		ArrayList<NodeProperty> props = new ArrayList<NodeProperty>();
		for (NodeAttribute a : mAttributeList)
			props.add(a);
		
		// TraitInstances:
		for (TraitInstance ti : new TraitInstanceIterator())
			props.add(ti);	
		
		// Node fields:
		for (NodeProperty tup : mFixedTups)
			props.add(tup);
		
		return props;
	}

	/*
	 * Fixed Node Properties by name:
	 */
	public NodeProperty getFixedNodeProperty(int key) {
		return mFixedTups.get(key);   // a bit hacky, but the way mFixedTups is set up means this works.
	}

	/*
	 * notesAsJSON()
	 * Returns JSON representation of all local trial notes. For example:
	 * {"notes":[{"node_id":3, "timestamp":2343434, "note":"whatever", "userid":"fred"}, ... ]}
	 * MFK not sure we should return result, what could go wrong (other than coding problem)?
	 * If there are no notes, a null object is returned.
	 */
	private Result notesAsJSON()  {
		try {
			JSONObject json = new JSONObject();
			json.put(DS_SERVER_TOKEN, mServerToken);
			JSONArray jnotes = new JSONArray();
			// Select all local notes for this trial:
			String qry = String.format(Locale.US, "select N.* from %s N inner join %s TU on N.%s = TU.%s where TU.%s = %d and N.%s = 1",
					TABLE_NODE_NOTE, TABLE_NODE, TUN_NODE_ID, ND_ID, ND_TRIAL_ID, this.getId(), TUN_LOCAL);
			Cursor ccr = null;
			try {		
				ccr = g_db().rawQuery(qry, null);
				if (ccr.moveToFirst())
					do {
						JSONObject jnote = new JSONObject();
						jnote.put(TUN_NODE_ID, ccr.getInt(ccr.getColumnIndex(TUN_NODE_ID)));
						jnote.put(TUN_TIMESTAMP, ccr.getLong(ccr.getColumnIndex(TUN_TIMESTAMP)));
						jnote.put(TUN_NOTE, ccr.getString(ccr.getColumnIndex(TUN_NOTE)));
						jnote.put(TUN_USERID, ccr.getString(ccr.getColumnIndex(DM_USERID)));
						jnotes.put(jnote);
					} while (ccr.moveToNext());
			} finally { if (ccr != null) ccr.close(); }
			if (jnotes.length() == 0)
				return new Result(null);
			json.put("notes", jnotes);
			return new Result(json);
		} catch (Exception e) {
			return new Result(false, e.getMessage());
		}
	}
	
	
	/*
	 * localNodesAsJSON()
	 * Returns JSON representation of all ids locally create trial nodes. For example:
	 * {"nodes":[<newNodeId>, ... ]}  MFK make this an array of objects, preferably same
	 * format as sent server to client.
	 * MFK not sure we should return result, what could go wrong (other than coding problem)?
	 */
	private Result localNodesAsJSON()  {
		try {
			JSONObject json = new JSONObject();
			JSONArray jnodes = new JSONArray();
			// Select all local nodes for this trial:
			String qry = String.format(Locale.US, "select %s from %s where %s = %d and %s >= %d",
					ND_ID, TABLE_NODE,
					ND_TRIAL_ID, this.getId(),
					ND_ID, Database.MIN_LOCAL_ID);
			Cursor ccr = null;
			try {		
				ccr = g_db().rawQuery(qry, null);
				if (ccr.moveToFirst())
					do {
						jnodes.put(ccr.getLong(ccr.getColumnIndex(ND_ID)));
					} while (ccr.moveToNext());
			} finally { if (ccr != null) ccr.close(); }
			json.put(JTRL_NODES_ARRAY, jnodes);
			return new Result(json);
		} catch (Exception e) {
			return new Result(false, e.getMessage());
		}
	}
	
	
	
	/*
	 * getAttributes()
	 * Returns copy of mAttributeList.
	 * MFK Should we really be returning the actual list rather than a copy?
	 */
	public ArrayList<NodeAttribute> getAttributes() {
		return new ArrayList<NodeAttribute>(mAttributeList);
	}
	
	/*
	 * getAttributeById()
	 * Returns attribute with specified id - iff it is in this trial, otherwise null.
	 */
	NodeAttribute getAttributeById(long id) {
		for (NodeAttribute nat : mAttributeList)
			if (nat.getId() == id) return nat;
		return null;
	}
	
	/*
	 * fillTrialNodesFromDB()
	 * Get the nodes for this trial from the db and fill the node list.
	 * I would like to have this as a static Node method (because of it's knowledge
	 * of the node table, but internal classes can't have static methods it seems.
	 * NB This really inline code, works on instance vars, assumed initialized.
	 */
	private void fillTrialNodesFromDB() {
		Cursor ccr = null;
		try {		
		// Get the nodes:
			ccr = g_db().query(TABLE_NODE, new String[] { ND_ID, ND_LOCAL, ND_ROW, ND_COL, ND_DESC, ND_BARCODE, ND_LATITUDE, ND_LONGITUDE },
					ND_TRIAL_ID + " = " + getId(), null, null, null, null);
			if (ccr.moveToFirst()) {
				do {
					// get location, if present:
					Location loc = null;
					if (!ccr.isNull(ccr.getColumnIndex(ND_LATITUDE))) {
						loc = Util.MakeLocation(ccr.getDouble(ccr.getColumnIndex(ND_LATITUDE)),
								ccr.getDouble(ccr.getColumnIndex(ND_LONGITUDE)));
					}
					addNodeToList(new Node(ccr.getInt(ccr.getColumnIndex(ND_ID)),
							ccr.getInt(ccr.getColumnIndex(ND_LOCAL)),
							ccr.getInt(ccr.getColumnIndex(ND_ROW)),
							ccr.getInt(ccr.getColumnIndex(ND_COL)),
							ccr.getString(ccr.getColumnIndex(ND_DESC)),
							ccr.getString(ccr.getColumnIndex(ND_BARCODE)),
							loc));
				} while (ccr.moveToNext());
			} // else should we worry if no nodes?
		} finally { if (ccr != null) ccr.close(); }
	}

	/*
	 * nodeIdIsNotLocalSQL()
	 * SQL condition to test if a node_id of a local node (without
	 * accessing the database). Ideally should be in class Node, but
	 * inner classes can't have static methods. Not great from an
	 * information hiding point of view, but helpful for performance.
	 */
	static private String nodeIdIsNotLocalSQL() {
		return String.format(Locale.US, "%s < %d", DM_NODE_ID, Database.MIN_LOCAL_ID);
	}


	// ==============================================================================================================
	// CLASSES: =====================================================================================================
	// ==============================================================================================================

	// Class Node: --------------------------------------------------------------------------------------------
	class Node {
		
		// DATA-INSTANCE: ====================================================================================================
		private long mId = NOT_SET_YET;   // This should be set to the id provided by the server.
		private int mRow;
		private int mCol;
		private int mLocal;               // zero for non-local, else a positive order number (1..numLocals)
		private String mDescription;
		private String mBarcode;
		private Location mLocation;       // gps location, null means no associated location
//		private int mFiltered = 0;        // non-zero means filtered out

		// METHODS-INSTANCE: =================================================================================================
		private Node(int id, int local, int row, int col, String description, String barcode, Location location) {
			this.mId = id;
			this.mLocal = local;
			this.mRow = row;
			this.mCol = col;
			this.mDescription = description;
			this.mBarcode = barcode;
			this.mLocation = location;
		}

		public long getId() { return mId; }
		public int getRow() { return mRow; }
		public int getCol() { return mCol; }
		public String getBarcode() { return mBarcode; }
//		private void setFilter(int val) {mFiltered = val; }
//		public boolean isFiltered() { return mFiltered != 0; }
		
		
//		public void setLocation(double latitude, double longitude) {
//			mLocation = Util.MakeLocation(latitude, longitude);
//		}
		public Location getLocation() { return mLocation; }
		public String getLocationString() {
			if (!hasLocation()) return "none";
			else
				return GPSListener.gpsLocationString(mLocation.getLatitude(), mLocation.getLongitude());
		}
		public boolean hasLocation() { return mLocation != null; }
		
		
		//*** Local node support: ----------------------------------------------------------------
		/*
		 * isLocal()
		 * Is this a locally created ("new") node.
		 * Currently encoded by row < 0. And col gives a numbering
		 * of the local nodes, starting from 1.
		 */
		public boolean isLocal() {
			return mLocal > 0;
		}
		/*
		 * localNumber()
		 * Returns zero if the node is not local, otherwise the number
		 * of the local node (from local node creation order starting from 1).
		 */
		public int localNumber() {
			return mLocal;
		}
		/*
		 * convertLocal2ServerNode()
		 * If this node is local, change it to a server node, with the given server id.
		 */
		private boolean convertLocal2ServerNode(int id) {
			if (!isLocal()) return false;
			mLocal = 0;
			// Update the db - we can't just use updateDB() as that won't work for updating the id.
			// Remember current id as we need it to identify the node in the db to be updated:
			long currentId = mId;
			mId = id;
			ContentValues values = new ContentValues();
			values.put(ND_ID, mId);
			values.put(ND_LOCAL, mLocal);
			if (g_db().update(TABLE_NODE, values, String.format("%s=%d", ND_ID, currentId), null) != 1)
				return false;
			return true;			
		}
		
		/*
		 * getAttributes()
		 * Return as a map the set of key:values pairs for this node.
		 * plus the fixed attributes (those in the node table itself) - until we make these normal attributes.
		 * NB keys and vals are passed in, not created here, any existing content is lost.
		 * Returns the number of elements placed in each list.
		 */
		public int getAttributes(ArrayList<String> keys, ArrayList<String> vals) {
			keys.clear();
			vals.clear();
			int count = 0;

			// Add the standard attributes:
			if (mDescription != null) {
				++count;
				keys.add("Description");
				vals.add(mDescription);
			}
			if (mBarcode != null) {
				++count;
				keys.add("Barcode");
				vals.add(mBarcode);
			}

			// Add the indirect attributes:
			String qry = String.format(Locale.US, "select %s, %s from %s inner join %s on %s = %s where %s = %d", TUA_NAME, AV_VALUE,
					TABLE_NODE_ATTRIBUTE, TABLE_ATTRIBUTE_VALUE, TUA_ID, AV_NATT_ID, AV_NODE_ID, mId);
			Cursor ccr = null;
			try {		
				ccr = g_db().rawQuery(qry, null);
				if (ccr.moveToFirst())
					do {
						++count;
						keys.add(ccr.getString(ccr.getColumnIndex(TUA_NAME)));
						vals.add(ccr.getString(ccr.getColumnIndex(AV_VALUE)));
					} while (ccr.moveToNext());
			} finally { if (ccr != null) ccr.close(); }
			return count;
		}

		/*
		 * InsertDB()
		 * Inserts to DB - assumes doesn't already exist there, it is not an overwrite.
		 */		
		private ContentValues makeContentValue() {
			ContentValues values = new ContentValues();
			values.put(ND_ID, mId);
			values.put(ND_TRIAL_ID, Trial.this.getId());
			values.put(ND_LOCAL, mLocal);
			values.put(ND_ROW, mRow);
			values.put(ND_COL, mCol);
			values.put(ND_DESC, mDescription);
			values.put(ND_BARCODE, mBarcode);
			if (hasLocation()) {
				values.put(ND_LATITUDE, mLocation.getLatitude());
				values.put(ND_LONGITUDE, mLocation.getLongitude());
			}
			return values;
		}
		private int InsertOrUpdateDB() {
			// Generate ID before saving if necessary:
			if (mId == NOT_SET_YET)
				mId = Globals.g.getDatabase().GenerateNewID(TABLE_NODE, ND_ID);

			ContentValues values = makeContentValue();
			if (g_db().insertWithOnConflict(TABLE_NODE, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
				return -1;
			return 0;
		}
		private int updateDB() {
			ContentValues values = makeContentValue();
			if (g_db().update(TABLE_NODE, values, String.format("%s=%d", ND_ID, getId()), null) != 1)
				return -1;
			return 0;
		}
		
		/*
		 * getAttributeInt
		 * Get attribute value as an Integer. 
		 * MFK probably should only return not null if the attribute has a numeric type.
		 */
		private Integer getAttributeInt(String key) {
			// normal attributes
			for (NodeAttribute att : mAttributeList) {
				if (key.equals(att.mName)) {
					// db query - use cache instead						
					String qry = String.format(Locale.US, "select %s from %s where %s = %d and %s = %d", AV_VALUE, TABLE_ATTRIBUTE_VALUE, AV_NATT_ID,
							att.mID, AV_NODE_ID, mId);
					Cursor ccr = null;
					try {		
						ccr = g_db().rawQuery(qry, null);
						int numExists = ccr.getCount();
						if (numExists <= 0) {
							return null;
						}
						ccr.moveToFirst();
						return ccr.getInt(0);
					} finally { if (ccr != null) ccr.close(); }
				}
			}
			return null;
		}

		/*
		 * getPropertyString()
		 * Get value for property with specified name.
		 * NB not really considering datatype properly..
		 * Note code clone from getAttributeInt.
		 */
		public String getPropertyString(String key) {
			// Fixed node properties:
			for (NodeProperty np : mFixedTups) {
				if (np.name().equalsIgnoreCase(key)) 
					return np.valueString(this);
			}
			
			// normal attributes
			for (NodeAttribute att : mAttributeList) {
				if (key.equals(att.mName)) {
					// db query - use cache instead						
					String qry = String.format(Locale.US, "select %s from %s where %s = %d and %s = %d",
							AV_VALUE, TABLE_ATTRIBUTE_VALUE, AV_NATT_ID, att.mID, AV_NODE_ID, mId);
					Cursor ccr = null;
					try {		
						ccr = g_db().rawQuery(qry, null);
						int numExists = ccr.getCount();
						if (numExists <= 0) {
							return null;
						}
						ccr.moveToFirst();
						//ccr.getType(0);
						return ccr.getString(0);
					} finally { if (ccr != null) ccr.close(); }
				}
			}
			return null; 
		}

		public Result setAttributeValue(NodeAttribute att, String attVal) {
			ContentValues values = new ContentValues();
			values.put(AV_NATT_ID, att.id());
			values.put(AV_NODE_ID, getId());
			values.put(AV_VALUE, attVal);
			if (g_db().insert(TABLE_ATTRIBUTE_VALUE, null, values) < 0) {
				return new Result(false, "Cannot add attribute to node");
			}
			return new Result();
		}
		
		//*** Note stuff: **************************************************************
		
		/*
		 * getNoteList()
		 */
		ArrayList<Note> getNoteList() {
			ArrayList<Note> nlist = new ArrayList<Note>();
			Cursor ccr = null;
			try {
				ccr = g_db().query(TABLE_NODE_NOTE, null, 
						String.format("%s = %d", TUN_NODE_ID, Node.this.getId()),
						null, null, null, null);
				if (ccr.moveToFirst()) {
					do {
						Note n = new Note();
						n.mId = ccr.getLong(ccr.getColumnIndex(TUN_ID));
						n.mText = ccr.getString(ccr.getColumnIndex(TUN_NOTE));
						n.mLocal = ccr.getInt(ccr.getColumnIndex(TUN_LOCAL));
						n.mTimestamp = ccr.getLong(ccr.getColumnIndex(TUN_TIMESTAMP));
						n.mUserId = ccr.getString(ccr.getColumnIndex(TUN_USERID));
						nlist.add(n);
					} while (ccr.moveToNext());
				}
			} finally { if (ccr != null) ccr.close(); }
			return nlist;				
		}
		
		class Note {
			private long    mId = -1;   // local table id
			private String  mText;
			private long    mTimestamp;
			private String  mUserId;
			private int     mLocal = 1;
	
			Note(String s) {
				mText = s;
			}
			private Note() {
			}
				
			public void Save() {
				// put in timestamp and userid
				ContentValues values = new ContentValues();
				values.put(TUN_NOTE, mText);
				values.put(TUN_NODE_ID, Node.this.getId());

				// Write (and record) current userid, timestamp, longitude and latitude:
				values.put(TUN_USERID, mUserId = Globals.getUsername());
				values.put(TUN_TIMESTAMP, mTimestamp = System.currentTimeMillis());
				values.put(TUN_LOCAL, mLocal);
				mId = g_db().replace(TABLE_NODE_NOTE, null, values);
				if (mId < 0)
					Util.msg("Failed creation of new node note record");
			}
			
			/*
			 * Update()
			 * Rewrite text into DB, assuming record exists.
			 * Resets other attributes only iff updateMetadata.
			 */
			public void Update(boolean updateMetadata) {
				ContentValues values = new ContentValues();
				values.put(TUN_NOTE, mText);
				if (updateMetadata) {
					values.put(TUN_USERID, mUserId = Globals.getUsername());
					values.put(TUN_TIMESTAMP, mTimestamp = System.currentTimeMillis());
					values.put(TUN_LOCAL, mLocal);
				}
				String whereClause = String.format(Locale.US, "%s=%d", TUN_ID, mId);
				g_db().update(TABLE_NODE_NOTE, values, whereClause, null);
			}
			
			public void Delete() {
				if (mId < 0) {
					Util.msg("Not in database");
					return;
				}
				String whereClause = String.format(Locale.US, "%s = %d", TUN_ID, mId);
				if (1 != g_db().delete(TABLE_NODE_NOTE, whereClause, null)) {
					Util.msg("Delete may have failed");
				}
			}
			
			public boolean isLocal() {
				return mLocal == 1;
			}
			public String getText() {
				//return Util.Timestamp2String(mTimestamp) + " - " + mUserId + "\n" + mText;
				//return Util.Timestamp2String(mTimestamp) + " - " + mUserId + ": " + mText;
				return mText;
			}
			
			/*
			 * toString()
			 * Just return first line of text
			 */
			public String toString() {
				int firstLineBreak = mText.indexOf("\n");
				if (firstLineBreak < 0) return mText;
				return mText.substring(0, firstLineBreak);  // just return first line
			}
			public void setText(String text) {
				mText = text;
			}
			public CharSequence Details() {
				return Util.timestamp2String(mTimestamp) + "\n"
						+ "User: " + mUserId + "\n" 
						+ mText;
			}
		}
	}

	//*** Attribute Stuff: *************************************************************
	
	/*
	 * Class NodeAttribute
	 */
	class NodeAttribute extends NodeProperty implements SelectorWidget.Selectee {
		String mName;
		long mID;
		Datatype mDatatype = T_STRING;
		int mFunc = 0;

		public NodeAttribute(long id, String name, Datatype datatype, int func) {
			setId(id);
			setName(name);
			mDatatype = datatype;
			mFunc = func;
		}

		@Override
		public String name() {
			return mName;
		}

		public void setName(String name) {
			this.mName = name;
		}

		public void setId(long mID) {
			this.mID = mID;
		}
		public long getId() {
			return mID;
		}
		public long selId() { return getId(); }

		@Override
		Datatype datatype() {
			return mDatatype;
		}

		@Override
		public long id() {
			return mID;
		}

		@Override
		NodePropertySource source() {
			return NodePropertySource.ATTRIBUTE;
		}

		@Override
		String valueString(Node node) {
			// Note this is returning a string even for numeric types. Not sure how that will work..
			Cursor ccr = null;
			try {
				String qry = String.format(Locale.US, "select %s from %s where %s = %d and %s = %d",
						AV_VALUE, TABLE_ATTRIBUTE_VALUE, AV_NATT_ID,
						mID, AV_NODE_ID, node.getId());
				ccr = g_db().rawQuery(qry, null);
				int numExists = ccr.getCount();
				if (numExists <= 0) {
					return null;
				}
				ccr.moveToFirst();
				return ccr.getString(0);
			} finally { if (ccr != null) ccr.close(); }
		}


		@Override
		Object valueObject(Node nd) {
			switch (mDatatype) {
			case T_DECIMAL:
				return valueNumeric(nd);
			case T_DATE:
			case T_INTEGER:
				return nd.getAttributeInt(name());
			case T_STRING:
				return valueString(nd);
			case T_LOCATION:
			case T_NONE:
			case T_PHOTO:
			case T_CATEGORICAL:
			default:
				return null;
			}
		}

		@Override
		Double valueNumeric(Node nd) {
			// First return null if not a numeric:
			if (!mDatatype.isNumeric())
				return null;
			
			/*
			 * Now retrieve value from database, assuming it is int or double according to datatype.
			 */
			String qry = String.format(Locale.US, "select %s from %s where %s = %d and %s = %d",
					AV_VALUE, TABLE_ATTRIBUTE_VALUE,
					AV_NATT_ID, this.mID,
					AV_NODE_ID, nd.getId());
			Cursor ccr = null;
			try {		
				ccr = g_db().rawQuery(qry, null);
				if (ccr.getCount() <= 0)
					return null;
				ccr.moveToFirst();
				switch (mDatatype) {
				case T_INTEGER:
				case T_CATEGORICAL:
				case T_DATE:
					Integer ival = ccr.getInt(0);
					if (ival == null) return null;
					return new Double(ival.doubleValue());
				case T_DECIMAL:
					return ccr.getDouble(0);
				default: return null;
				}
			} finally { if (ccr != null) ccr.close(); }
		}

		class AttDatum implements IDatum {
			long mId = -1;
			protected Node mNode;

			 // value                  

			// should represent attributeValue table rows
			
			@Override
			public boolean hasDBValue() {
				Node node = Trial.this.getCurrNode();
				
				// db query - use cache instead						
				String qry = String.format(Locale.US,
						"select %s from %s where %s = %d and %s = %d", AV_VALUE, TABLE_ATTRIBUTE_VALUE,
						AV_NATT_ID, NodeAttribute.this.getId(), AV_NODE_ID, node.getId());
				Cursor ccr = null;
				try {
					ccr = g_db().rawQuery(qry, null);
					int numExists = ccr.getCount();
					if (numExists <= 0) {
						return false;
					}
				} finally { if (ccr != null) ccr.close(); }
				return true;
			}

			@Override
			public boolean isNA() {
				return false;
			}

			@Override
			public String ValueAsString(String absentVal) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public boolean navigatingAway() {
				// TODO Auto-generated method stub
				return false;
			}
		}
		@Override
		IDatum getCurrentIDatum() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean hasScore() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public String selectText() {
			return name();
		}

		@Override
		public void selected() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void unselected() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public ArrayList<?> getDistinctValues() {
			ArrayList<Object> vals = new ArrayList<Object>();						
			String qry = String.format(Locale.US,
					"select distinct %s from %s where %s = %d", AV_VALUE, TABLE_ATTRIBUTE_VALUE,
					AV_NATT_ID, getId());
			Cursor ccr = null;
			try {
				ccr = g_db().rawQuery(qry, null);
				if (ccr.moveToFirst())
					do {
						switch (mDatatype) {
						case T_CATEGORICAL:
							// Have to get the category values here..
							break;
						case T_DATE:
						case T_INTEGER:
							vals.add(ccr.getInt(0));
							break;
						case T_DECIMAL:
							vals.add(ccr.getDouble(0));
							break;
						case T_LOCATION:
							break;
						case T_NONE:
							break;
						case T_PHOTO:
							break;
						case T_STRING:
							vals.add(ccr.getString(0));
							break;
						default:
							break;
						}
					} while (ccr.moveToNext());
			} finally { if (ccr != null) ccr.close(); }
			return vals;
		}
	}
	
	/*
	 * getAttribute()
	 * Returns attribute with given name or id, provided it is in mAttributeList, otherwise null.
	 */
	private NodeAttribute getAttribute(String name) {
		for (NodeAttribute att : mAttributeList)
			if (att.name().equals(name))
				return att;
		return null;
	}
	public NodeAttribute getAttribute(long id) {
		for (NodeAttribute att : this.mAttributeList)
			if (att.getId() == id)
				return att;
		return null;
	}
	
	/*
	 * clearAttributes()
	 * Delete all attributes (and their values) from the trial.
	 */
	void clearAttributes() {
		// Delete attributes (note this should cause cascade to delete attribute values):
		String whereClause = String.format(Locale.US, "%s = %d", TUA_TRIAL_ID, this.getId());
		g_db().delete(TABLE_NODE_ATTRIBUTE, whereClause, null);
		mAttributeList = new ArrayList<NodeAttribute>();
	}
	
	//*** TraitInstance: ***************************************************************

	/*
	 *  Class TraitInstance:
	 */
	class TraitInstance extends NodeProperty implements SelectorWidget.Selectee {
		// INSTANCE DATA: ==============================================================================================
		private long m_id = -1;
		private Trait mTrait;
		private int mDayCreated;
		private int mSeq; // intraday num
		private int mSampNum;
		private RepSet mRepset;
		private boolean mDirtyBit;
			// not used or fully implemented yet, needs to be stored in db, and updated directly.
			// could go to also having dirty bit on datum (or'ing together for ti dirty bit), for efficient upload.
			// this would pay off particularly in photos perhaps..
		
		
		private Datum mCachedDatum = null;
			// To avoid repeated unneccesary db calls to retrieve a datum from this traitinstance for a
			// given node we cache each such retrieval. Beware however of datum attributes that are not from
			// the database, if they change, then the cached version may be different in these attributes
			// compared to what they would be if created by new (which is what happens for a db retrieval).
			// MFK - perhaps if a cached one is available we should do a new, and copy over the db fields?
			// but don't we sometimes want any changed values?
		
		// METHODS: ==============================================================================================
		
		/*
		 *  Constructor:
		 *  For new traitInstance that is already in the db.
		 */
		private TraitInstance(long id, Trait trt, int dayNum, int seq, int sampNum, RepSet rs) {
			this.m_id = id;
			this.mTrait = trt;
			this.mDayCreated = dayNum;
			this.mSeq = seq;
			this.mSampNum = sampNum;
			mRepset = rs;
		}

		/*
		 *  Constructor:
		 *  For new traitInstance (i.e. one not yet in database).
		 *  The new instance is added to the db.
		 */
		private TraitInstance(Trait trt, int dayNum, int seq, int sampNum, RepSet rs) {
			this(-1, trt, dayNum, seq, sampNum, rs);
			
			// Add to the database, and set the _id field with the auto inc'd DB value:
			ContentValues values = new ContentValues();
			values.put(TI_TRIAL_ID, Trial.this.getId());
			values.put(TI_TRAIT_ID, mTrait.getId());
			values.put(TI_DAYCREATED, mDayCreated);
			values.put(TI_SEQNUM, mSeq);
			values.put(TI_SAMPNUM, mSampNum);
			m_id = g_db().insert(TABLE_TRAIT_INSTANCE, null, values);
			if (m_id < 0)
				Util.msg("Failed creation of new trait instance");		
		}
		
		/*
		 * Getter functions:
		 */
		Trait getTrait() {
			return mTrait;
		}
		
		Trial getTrial() {
			return Trial.this;
		}

		long getId() {
			return m_id;
		}
		public long selId() { return getId(); }

		public String getTraitName() {
			return mTrait.getCaption();
		}

		public int getSampleNum() {
			return mSampNum;
		}

		public int getSeqNum() {
			return mSeq;
		}
		
		/*
		 * getExportSeqNum()
		 * We cannot use the local seq num for export because this may be the same as the seqNum
		 * of a deleted repset. The server requires a unique "seqNum" for each repset, but it doesn't
		 * require them to be contiguous. We use the minimum TraitInstance id from the repset, which
		 * serves the purpose. We should probably think of this as a scoreSet ID.
		 */
		public long getExportSeqNum() {
			return mRepset.getMinTraitInstanceId();
		}
		
		public int getCreationDate() {
			return mDayCreated;
		}

		public String getFullName() {
			return getTraitName() + "_" + getCreationDate() + "." + getSeqNum() + "." + getSampleNum();
		}	
		
		public boolean isDirty() {
			return mDirtyBit;
		}

		public void setDirtyBit(boolean mDirtyBit) {
			this.mDirtyBit = mDirtyBit;
		}

		public RepSet getRepset() {
			return mRepset;
		}

		public long numScores() {
			String whereClause = String.format(Locale.US, "%s = %d", DM_TRAITINSTANCE_ID, m_id);
			long numData = DatabaseUtils.queryNumEntries(g_db(), TABLE_DATUM, whereClause);
			return numData;
		}
		
		public long numNA() {
			String whereClause = String.format(Locale.US, "%s = %d and %s is null",
					DM_TRAITINSTANCE_ID, m_id, DM_VALUE);
			long numData = DatabaseUtils.queryNumEntries(g_db(), TABLE_DATUM, whereClause);
			return numData;
		}
		
		/*
		 * getScoringName()
		 * When scoring, only one repset of same trait can be present at a time, so seqnum skipped.
		 */
		public String getScoringName() {
			String name = getTraitName();
			if (getRepset().isDynamic()) {
				name += " " + (getSampleNum() + 1) + " of " + this.getRepset().numReps();
			} else if (getRepset().numReps() > 1) {
				name += "." + getSampleNum();
			}
			name += "("; 
			name += numScores();
			name += ")";
			return name;
			//return getTraitName() + (getRepset().size() > 1 ? ("." + getSampleNum()) : "");
		}	
		
		/*
		 * GetDatumCursor()
		 * Returns cursor for the NON-LOCAL datum for this ti.
		 * Cursor has all fields of the datum table.
		 * We could filter to non local by joining to the node table and checkin local flag,
		 * but instead we rely on the fact that locality can be determined directly from the
		 * node_id (in the datum). This is certainly undesirable from a information hiding point
		 * of view, but this speed of this function may be noticeable to the user (although I
		 * haven't tested). Hence we break the rules a little here, moving the details of the
		 * test back to the node class as best we can.
		 */
		public Cursor getDatumCursor() {
			String whereClause = String.format(Locale.US, "%s = %d and %s", DM_TRAITINSTANCE_ID, getId(), nodeIdIsNotLocalSQL());
			// MFK have to only get latest?		note loop below assumes there may be multiple, should error if so..
			return g_db().query(TABLE_DATUM, null, whereClause, null, null, null, null);		
		}

		/*
		 * getUnsavedDatumCursor()
		 * Returns cursor for the unsaved datums for this ti.
		 * Cursor has all fields of the datum table.
		 */
		public Cursor getUnsavedDatumCursor() {
			String whereClause = String.format(Locale.US, "%s = %d and %s = 0", DM_TRAITINSTANCE_ID, getId(), DM_SAVED);
			return g_db().query(TABLE_DATUM, null, whereClause, null, null, null, null);		
		}
		
		/*
		 * getDatum()
		 * Return datum for this TraitInstance for the trial current node.
		 * Note there may be no datum in the db, so a newly created (and unsaved) datum
		 * may be returned.
		 * Note we are currently assuming there is only one of these, which may change.
		 * If error, emits message and returns null.  MFK, the calling functions are not (attow)
		 * checking for null, but this error is unexpected.
		 */
		public Datum getDatum() {
			Node currNode = Trial.this.getCurrNode();
			
			// Return cached datum if appropriate:
			if (mCachedDatum != null && mCachedDatum.mNodeId == currNode.getId()) {
				mCachedDatum.reset();
				return mCachedDatum;
			}

			// Otherwise get from the database, or make new:
			Result res = Datum.getFromDB(this, currNode);
			if (res.good())
				mCachedDatum = (Datum)res.obj();
			else {
				Util.msg(res.errMsg());
				mCachedDatum = null;
			}
			return mCachedDatum;
		}

		public void clearCachedDatum() {
			mCachedDatum = null;
		}

		@Override
		String name() {
			return getFullName();
		}

		@Override
		Datatype datatype() {
			return mTrait.getType();
		}

		@Override
		long id() {
			return this.getId();
		}

		@Override
		NodePropertySource source() {
			return NodePropertySource.SCORE;
		}

		@Override
		String valueString(Node tu) {
			/*
			 *  MFK getfromdb creates datum even if none in db,
			 *  we need its to string to return "no score" if necessary.
			 *  Or should return null for no score.
			 */
			
			Result res = Datum.getFromDB(this, tu);
			if (res.good())
				return ((Datum)res.obj()).ValueAsString("");
			else {
				return "error";
			}
		}

		@Override
		Object valueObject(Node tu) {
			// don't want to go to db, may be testing putative val
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		Double valueNumeric(Node tu) {
			// MFK not needed ATTOW, probably will be, but there may be a different solution then..
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		ArrayList<?> getDistinctValues() {
			return null;
		}

		public TraitInstance next() {
			return getRepset().getTI(getSampleNum() + 1);
		}

		public TraitInstance prev() {
			return getRepset().getTI(getSampleNum() - 1);
		}

		public boolean isDynamic() {
			return getRepset().isDynamic();
		}

		public void dynamicAutoProgress() {
		}

		@Override
		IDatum getCurrentIDatum() {
			return getDatum();
		}

		@Override
		public boolean hasScore() {
			return getDatum().hasDBValue();
		}

		@Override
		public String selectText() {
			return this.getScoringName();
		}

		@Override
		public void selected() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void unselected() {
			// TODO Auto-generated method stub
			
		}
	}
	
	//*** RepSet: **********************************************************************

	/*
	 *  Class RepSet: -----------------------------------------------------------------------------------------------
	 *
	 * Wraps a set of replicate TraitInstances.
	 * MFK Not a db entity, hence no proper id. It really should be in db. In any case, can
	 * use min traitInstance Id as id.
	 */
	class RepSet {
		private Trait mTrait;
		private int mDayCreated;
		private int mSeq;
		private ArrayList<TraitInstance> mReps = new ArrayList<TraitInstance>(); // TIs must be in samp num order
		
		/*
		 * Constructor.
		 */
		RepSet(Trait trt, int dayCreated, int instNum) {
			this.mTrait = trt;
			this.mDayCreated = dayCreated;
			this.mSeq = instNum;
		}

		public TraitInstance getTI(int i) {
			if (i < 0 || i > mReps.size() - 1)
				return null;
			return mReps.get(i);
		}

		/*
		 * Constructor.
		 * For case when number of samples is known. Note that if count
		 * is zero, this is a dynamic scoreset, and identified as such in the db
		 * by virtue of having initial sample num zero.
		 * Note trait instances are created and added to the database. Perhaps not appropriate in here?
		 */
		RepSet(Trait trt, int dayCreated, int instNum, int count) {
			this(trt, dayCreated, instNum);
			
			/*
			 * We need to create one RepSet, and a TraitInstance for each rep.
			 * Note that if count is zero, we create a single ti with sample number zero,
			 * this is a "dynamic" scoreset.
			 * NB the tis are added to the list in order of increasing sample number.
			 * We rely on this to identify dynamic repsets.
			 */
			for (int samp = (count == 0 ? 0 : 1); samp <= count; ++samp) {
				add(new TraitInstance(trt, dayCreated, instNum, samp, this)); // db add in here
			}

		}
		
		@Override
		public String toString() {
			return getDescription();
		}

		public void add(TraitInstance ti) {
			mReps.add(ti);
		}
		
//		public String dataAsJSON() {
//			String out = "{";
//			for (TraitInstance ti : mReps) {
//				Datum [] data = ti.getData();
//				for (Datum d : data) {
//					out += d.toString()
//				}
//				out += 
//			}
//			out += "}";
//			return out;
//		}
		
		/*
		 * append()
		 * Add new TI to dynamic repset();
		 * NB dayCreated is that of first TI (not current date)
		 */
		public void append() {
			if (!isDynamic())
				return;
			TraitInstance first = mReps.get(0);
			int sampNum = mReps.size();  // Note if not dynamic, this would be size + 1
			add(new TraitInstance(getTrait(), first.getCreationDate(),
					first.getSeqNum(), sampNum, this)); // db add in here
		}

		public TraitInstance[] getTraitInstanceList() {
			return mReps.toArray(new TraitInstance[mReps.size()]);
		}
		
		public TraitInstance getFirst() {
			return mReps.get(0);
		}

		/*
		 * getMinTraitInstanceId()
		 * Return minimum id of the TraitInstances.
		 * Can be used as unique id for repset.
		 */
		public long getMinTraitInstanceId() {
			long min = 0; 
			for (TraitInstance ti : mReps) {
				if (min == 0 || ti.getId() < min)
					min = ti.getId();
			}
			return min;
		}
		
		public long getId() { return getMinTraitInstanceId(); }
		
		/*
		 * isDynamic()
		 * A dynamic repset is one which can have trait instances
		 * added while scoring. This is currently indicated by
		 * the first trait instance have a sample number of zero
		 * rather than the usual one.
		 */
		public boolean isDynamic() {
			return mReps.get(0).mSampNum == 0;
		}

		/*
		 * getDescription()
		 * <traitName>_<seqnum> <dayCreated>
		 * plus " (<numReps> samples)" if more than one rep.
		 */
		public String getDescription() {
			String prefix = mTrait.getCaption() + "_" + mSeq + " " + mDayCreated;
			String postfix = "";
			if (isDynamic())
				postfix = " (dynamic)";
			else if (mReps.size() > 1)
				postfix = " (" + mReps.size() + " samples)";
			return prefix + postfix;
//			return mTrait.getCaption() + "_" + mSeq + " " + mDayCreated +
//					(mReps.size() > 1 ? " (" + mReps.size() + " samples)" : "");
		}

		public Trait getTrait() {
			return mTrait;
		}

		public int numReps() {
			return mReps.size();
		}
		
		public int numScores() {
			int count = 0;
			for (TraitInstance ti : getTraitInstanceList())
				count += ti.numScores();
			return count;
		}
		public int numNA() {
			int count = 0;
			for (TraitInstance ti : getTraitInstanceList())
				count += ti.numNA();
			return count;
		}

		
		/*
		 * stats()
		 * Statistics for this repset, including trait type specific info.
		 */
		public String stats() {
			int numScores = numScores();
			int naCount = numNA();
			String out = String.format(Locale.US, "Name: %s\nNumber of Scores: %d\nNumber of NA Scores: %d\n",
					toString(), numScores, naCount);
		
			if (numScores - naCount > 0)
				out += mTrait.repSetStats(this);
			return out;
		}
		
		/*
		 * SQLFormatTI_IDList()
		 * Return list of trait instance ids in format for sql "in",
		 * eg "(1,2,3)".
		 */
		public String SQLFormatTI_IDList() {
			String ti_ids = "(";
			TraitInstance[] tiList = getTraitInstanceList();
			for (int i = 0; i < tiList.length; ++i) {
				if (i > 0)
					ti_ids += ",";
				ti_ids += tiList[i].getId();
			}
			ti_ids += ")";
			return ti_ids;
		}

		/*
		 * getFirstUnscored()
		 * Return first ti without a score in db for current node, or null if there is none.
		 */
		public TraitInstance getFirstUnscored() {
			for (TraitInstance ti : mReps) {
				if (!ti.getDatum().hasDBValue())
					return ti;
			}
			return null;
		}
	}
	

	/*
	 * TraitInstanceIterator
	 * Iterator for the trait Instances within the trial.
	 * These are given in repset order using the mRepSetList.
	 * Behaviour undefined if mRepSetList changed while iterating.
	 */
	private class TraitInstanceIterator implements Iterable<TraitInstance> {
		@Override
		public Iterator<TraitInstance> iterator() {
			return new Iterator<TraitInstance>() {
				int mRepsetIndex = 0;
				int mCurrTIindex;
				int mNumRepsets = mScoreSets.size();
				
				@Override
				/*
				 * hasNext()
				 * NB, this guaranteed to leave mRepsetIndex and mCurrTIindex set appropriately.
				 */
				public boolean hasNext() {
					if (mRepsetIndex >= mNumRepsets)
						return false;
					if (mCurrTIindex < mScoreSets.get(mRepsetIndex).numReps())
						return true;
					
					// Current repset finished, move to next:
					++mRepsetIndex;
					mCurrTIindex = 0;
					return (mRepsetIndex < mNumRepsets && mCurrTIindex < mScoreSets.get(mRepsetIndex).numReps());
				}

				@Override
				public TraitInstance next() {
					if (hasNext())
						return mScoreSets.get(mRepsetIndex).getTI(mCurrTIindex++);
					return null;
				}

				@Override
				public void remove() {
					// Unsupported, and we don't bother to throw exception.
				}};
		}
	}
	
	/*
	 *  Classes and Enums for scoring state:
	 */
	public enum SortType {
		SORT_COLUMN_SERPENTINE, SORT_ROW_SERPENTINE, SORT_COLUMN, SORT_ROW, SORT_CUSTOM;
		public int value() {
			return this.ordinal();
		}
		static public SortType fromValue(int val) {
			if (val < SORT_COLUMN_SERPENTINE.ordinal() || val > SORT_CUSTOM.ordinal())
				return null;
			return values()[val];
		}
		public String text(Trial trial) {
			switch(this) {
			case SORT_COLUMN:
				return "By " + trial.getIndexName(1);
			case SORT_COLUMN_SERPENTINE:
				return "By " + trial.getIndexName(1) + " Serpentine";
			case SORT_CUSTOM:
				return "Custom";
			case SORT_ROW:
				return "By " + trial.getIndexName(0);
			case SORT_ROW_SERPENTINE:
				return "By " + trial.getIndexName(0) + " Serpentine";
			}
			return null;
		}
	}
	public enum SortDirection {
		ASC("Up"), DESC("Down"),
		ALT_UP_DOWN("UP then Down"),
		ALT_DOWN_UP("Down then Up");
		
		String mText;
		SortDirection(String text) { mText = text; }
		public int value() { return ordinal(); }
		static public SortDirection fromValue(int val) {
			if (val < 0 || val > values().length)
				return null;
			return values()[val];
		}
	    @Override
	    public String toString() {
	        return mText;
	    }
	}
}
