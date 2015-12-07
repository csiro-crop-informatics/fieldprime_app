package csiro.fieldprime;


public class DbNames {
	// Table and column names:
	// Note that there is a local sql database (which these Strings are for)
	// and also potentially a server database. The server db will have a similar
	// set of tables and fields, although this may not be exact. In general though
	// these names can be used for both the local and server database.
	public static final String TABLE_TRIAL = "trial";
	public static final String DS_ID = "_id";
	public static final String DS_SERVER_ID = "serverId";
	public static final String DS_SERVER_TOKEN = "serverToken";
	public static final String DS_NAME = "name";
	public static final String DS_FILENAME = "filename";
	public static final String DS_SITE = "site";
	public static final String DS_YEAR = "year";
	public static final String DS_ACRONYM = "acronym";
	public static final String DS_UPLOAD_URL = "uploadURL";
	public static final String DS_ADHOC_URL = "adhocURL";
	
	// Table trialProperty:
	public static final String TABLE_TRIAL_PROPERTY = "trialProperty";
	public static final String TA_TRIAL_ID = "trial_id";
	public static final String TA_NAME = "name";
	public static final String TA_VALUE = "value";

	// Tables Trait and Dat, these used by Trait class, hence public:
	// Alternatively, they might more properly reside in Trait now.
	public static final String TABLE_TRAIT = "trait";
	public static final String TR_ID = "id";
	public static final String TR_CAPTION = "caption";
	public static final String TR_DESCRIPTION = "description";
	public static final String TR_TYPE = "type";    // deprecated 25/3/15 replaced with TR_DATATYPE, delete when no old clients
	public static final String TR_DATATYPE = "datatype";
	public static final String TR_SYSTYPE = "sysType";
	public static final String TR_TID = "tid";
	public static final String TR_SERVER_ID = "serverId";
	public static final String TR_UPLOAD_URL = "uploadURL";
	public static final String TR_BARCODE = "barcodeAtt_id";   // NB in trait not trialTrait, unlike on the server
	                                                           // This because no sys traits on client (and easier).
	
	public static final String TABLE_TRIALTRAIT = "trialTrait";
	public static final String TT_TRIALID = "trial_id";
	public static final String TT_TRAITID = "trait_id";

	public static final String TABLE_NODE = "node";
	public static final String ND_ID = "id";
	public static final String ND_TRIAL_ID = "trial_id";
	public static final String ND_LOCAL = "local";
	public static final String ND_ROW = "row";
	public static final String ND_COL = "col";
	public static final String ND_DESC = "desc";
	public static final String ND_BARCODE = "barcode";
	public static final String ND_LATITUDE = "latitude";
	public static final String ND_LONGITUDE = "longitude";

	public static final String TABLE_NODE_ATTRIBUTE = "nodeAttribute";
	public static final String TUA_ID = "id";
	public static final String TUA_TRIAL_ID = "trial_id";
	public static final String TUA_NAME = "name";
	public static final String TUA_DATATYPE = "datatype";
	public static final String TUA_FUNC = "func";

	public static final String TABLE_ATTRIBUTE_VALUE = "attributeValue";
	public static final String AV_NATT_ID = "nodeAttribute_id";
	public static final String AV_NODE_ID = "node_id";
	public static final String AV_VALUE = "value";

	public static final String TABLE_TRAIT_INSTANCE = "traitInstance";
	public static final String TI_ID = "_id";
	public static final String TI_TRIAL_ID = "trial_id";
	public static final String TI_TRAIT_ID = "trait_id";
	public static final String TI_DAYCREATED = "dayCreated";
	public static final String TI_SEQNUM = "seqNum";
	public static final String TI_SAMPNUM = "sampleNum";

	public static final String TABLE_DATUM = "datum";
	public static final String DM_ID = "_id";
	public static final String DM_TRAITINSTANCE_ID = "traitInstance_id";
	public static final String DM_NODE_ID = "node_id";
	public static final String DM_TIMESTAMP = "timestamp";
	public static final String DM_GPS_LONG = "gps_long";
	public static final String DM_GPS_LAT = "gps_lat";
	public static final String DM_USERID = "userid";
	public static final String DM_VALUE = "value";
	public static final String DM_SAVED = "saved";
	
	public static final String TABLE_TRAIT_CATEGORY = "traitCategory";
	public static final String TC_TRAIT_ID = "trait_id";
	public static final String TC_VALUE = "value";
	public static final String TC_CAPTION = "caption";
	public static final String TC_IMAGE_URL = "imageURL";
	
	public static final String TABLE_NODE_NOTE = "nodeNote";
	public static final String TUN_ID = "id";
	public static final String TUN_NODE_ID = "node_id";
	public static final String TUN_NOTE = "note";
	public static final String TUN_TIMESTAMP = "timestamp";
	public static final String TUN_USERID = "userid";
	public static final String TUN_LOCAL = "local";

	public static final String TABLE_TRIALTRAITINTEGER = "trialTraitInteger";
	public static final String TTI_TRIALID = "trial_id";
	public static final String TTI_TRAITID = "trait_id";
	public static final String TTI_MIN = "min";
	public static final String TTI_MAX = "max";
	public static final String TTI_COND = "cond";

	public static final String TABLE_TRIALTRAITNUMERIC = "trialTraitInteger";
	public static final String TTN_TRIALID = "trial_id";
	public static final String TTN_TRAITID = "trait_id";
	public static final String TTN_MIN = "min";
	public static final String TTN_MAX = "max";
	public static final String TTN_COND = "cond";

	public static final String TABLE_TRAITPHOTO = "traitPhoto";
	public static final String TTP_TRAITID = "trait_id";
	public static final String TTP_URL = "photoURL";
	
	public static final String TABLE_TRAITSTRING = "traitString";
	public static final String TTS_TRAITID = "trait_id";
	public static final String TTS_PATTERN = "pattern";
	
	public static final String TABLE_TSTORE = "tstore";
	public static final String TS_VALUE = "value";
	public static final String TS_REF = "ref";
	public static final String TS_KEY = "key";


	/*
	 *  Strings for JSON (that aren't database names):
	 *  Ideally we would define these all here, rather than use the values
	 *  directly in the code. This would allow us to just come here to check or change
	 *  a name. These values must, of course, match the values used by the
	 *  server. So really ideally they would be in some text file that can be
	 *  referenced by both the server and (this) client code. This a little difficult
	 *  as currently the server and client are in different code repositories, and written
	 *  in different languages.
	 */
	public static final String JNODE_LOCATION = "location";
	public static final String JNODE_ATTVALS = "attvals";
	
	public static final String JTRL_NODE_ATTRIBUTES = "nodeAttributes";
	public static final String JTRL_TRIALUNITS_ARRAY = "trialUnits";   // Remove when all client at or past v362
	public static final String JTRL_NODES_ARRAY = "nodes";
	public static final String JTRL_YEAR = "year";
	public static final String JTRL_SITE = "site";
	public static final String JTRL_NAME = "name";
	public static final String JTRL_ACRONYM = "acronym";
	public static final String JTRL_SERVER_TOKEN = "serverToken";
	public static final String JTRL_UPLOAD_URL = "uploadURL";
	public static final String JTRL_ADHOC_URL = "adhocURL";
	public static final String JTRL_TRIAL_PROPERTIES = "trialProperties";
	
	
	public static final String JTRIAL_LIST_NAME = "name";
	public static final String JTRIAL_LIST_URL = "url";

	
	// Node names
	public static final String JND_ID = ND_ID;
	public static final String JND_ROW = ND_ROW;
	public static final String JND_COL = ND_COL;
	public static final String JND_DESC = "description";
	public static final String JND_BARCODE = ND_BARCODE;
	
	// validation:
	public static final String JTU_VALIDATION = "validation";
	public static final String JTV_PATTERN = "pattern";
	

	/*
	 * Strings for other things:
	 */
	public static final String TSSs = "Trait Score Sets";
	public static final String SSs = "Score Sets";
	
	/*
	 * Trial Property names:
	 */
	public static final String TRIAL_INDEX_NAME1 = "indexName1";
	public static final String TRIAL_INDEX_NAME2 = "indexName2";
	
}
