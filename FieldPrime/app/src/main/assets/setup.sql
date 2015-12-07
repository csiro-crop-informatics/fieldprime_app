--
-- Datareap Android Client SQLite database
--
-- SQL statements used to create the sqlite database.
-- Statements may be multi-line, BUT MUST start on a new line, and
-- be terminated with a semi-colon.
--
-- Discussion points
-- When a trait instance is created, should we populate the datum table
-- with null (or default) values? If not, then the absence of a datum record
-- would presumably be the equivalent of one existing with a null value. but
-- we might then have to code for these two cases. Another advantage of pre-filling
-- would be that if we are going to run out of space, we find out early.
-- Disadvantages: might be slow to add new trait instances?
--
--


--
-- trial
--
-- serverToken is also provided by the server.
-- NB serverId is no longer used.
-- nextLocalNodeId   integer default 24,000,000   // this should last as long as the serverToken.
--
create table trial(
  _id            integer primary key autoincrement,
  serverId       integer,
  serverToken    text,
  uploadURL      text,
  adhocURL       text,
  name           text unique,
  filename       text,
  site           text,
  year           text,
  acronym        text
);

--
-- trialProperty
--
-- Place to store additional per trial info without needing to alter
-- table structures (att short for attribute).
-- Currently allow only one value for each trial/name. We could
-- change this if necessary, but note some code, attow, relies
-- on this property and would need changing if this changed.
--
create table trialProperty(
  trial_id    integer,
  name        text,
  value       text,
  PRIMARY KEY (trial_id, name),
  FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
);

--
-- node
--
-- Currently an attribute of trial, but perhaps should have a site entity?
-- or trial entity.
-- Field local indicates whether the node is locally created (and not yet reified
-- by uploading to the server). If null the node is not local. If it is local
-- the value gives the creation order of the local nodes, starting from one.
-- 
create table node(
  id         INTEGER primary key,
  trial_id   integer,
  local      integer,
  row        integer,
  col        integer,
  desc       text,
  barcode    text,
  latitude   DOUBLE,
  longitude  DOUBLE,
  FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
);


-- 
-- nodeAttribute
--
-- To allow for arbitrary extra node attributes, not anticipated at design time.
-- 
create table nodeAttribute(
  id         integer primary key,
  trial_id   integer,
  name       text,
  datatype   int not null default 2,
  func       int not null default 0,
  UNIQUE (trial_id, name),
  FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
);


-- 
-- attributeValue
-- Note value is not typed, so may be text or numeric.
--
create table attributeValue(
  nodeAttribute_id   integer,
  node_id            integer,
  value,                  
  UNIQUE (nodeAttribute_id, node_id),
  FOREIGN KEY(nodeAttribute_id) REFERENCES nodeAttribute(id) ON DELETE CASCADE,
  FOREIGN KEY(node_id) REFERENCES node(id) ON DELETE CASCADE ON UPDATE CASCADE
);
-- Index on attributeValue, not currently used, but may help if attribute value
-- search gets too slow. Testing on one data set showed perhaps a second speed up
-- (if any), but with a 30 second slower trial download. NB, both these tests were
-- very cursory and may be inaccurate, but presumably having an index could not speed
-- up downloads, so leaving it out for the moment.
-- create index attributeValue_value_idx on attributeValue (value);

--
-- nodeNote
-- Field local is a boolean indicating whether or not this is locally generated
-- or a value from the server.
--
create table nodeNote(
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  node_id  integer,
  timestamp     integer,
  userid        text,
  note          text,
  local         int,
  UNIQUE (node_id, timestamp, note),
  FOREIGN KEY(node_id) REFERENCES node(id) ON DELETE CASCADE ON UPDATE CASCADE
);

--
-- trait
--
-- Field sysType is included in case we need to distinguish between traits other than
-- on their data types. EG we may have a standard set of traits that are pre set up for the
-- user to easily add to a data set - as opposed to traits that were created because they
-- were specified for a single trial (and hence perhaps should be deleted from the system
-- if that trial is deleted).
-- NB serverId should be a value specified by the server, or 0 for an ad-hoc trait.
-- NB id is not autoincrement as it maybe provided by the server.
-- Note barcodeAtt_id is in the trait table here, but in the trialTrait table on the server.
-- This is because here, there are no system traits and every trait has just one trial.
-- (Given this the trialTrait table itself could be removed, but it's doing no harm and is
-- already coded in). barcodeAtt_id could be move to trialTrait if that becomes beneficial.
-- uploadURL probably has the same issue, i.e. it is trial specific.
--
create table trait(
  id          INTEGER PRIMARY KEY,
  serverId    INTEGER NOT NULL,
  caption     TEXT,
  description TEXT,
  uploadURL   TEXT,
  type        INT,
  sysType     INT,
  flags       INT not null default 0,
  barcodeAtt_id integer,
  FOREIGN KEY(barcodeAtt_id) REFERENCES nodeAttribute(id) ON DELETE SET NULL
);


--
-- trait_category
--
-- Values for categorical trait
--
create table traitCategory(
  trait_id    integer NOT NULL,
  value       int NOT NULL,
  caption     text NOT NULL,
  imageURL    text,
  PRIMARY KEY (trait_id, value),
  FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE
);

--
-- traitPhoto
--
-- Values for photo trait
--
create table traitPhoto(
  trait_id    integer NOT NULL,
  photoURL    text not null,
  PRIMARY KEY (trait_id),
  FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE
);

--
-- trialTraitInteger
-- Extension of trait table for datatype integer.
-- But - we need trial specific trait attributes!
-- Should this instead refer to key of trialTrait?
-- NB, given that traits are now uniquely assigned to a trial
-- (no sys traits), the trial_id could be removed, and this
-- table renamed traitInteger, perhaps.
-- 
create table trialTraitInteger(
  trial_id   INT NOT NULL,
  trait_id   INT NOT NULL,
  min        INT,
  max        INT,
  cond       TEXT,
  PRIMARY KEY(trial_id, trait_id),
  FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE,
  FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
);

--
-- trialTraitNumeric
-- Extension of trait table for datatype integer or decimal.
-- NB SQLite should preserve string decimals to 15 decimal
-- places. MFK, currently trialling just for decimal.
-- But - we need trial specific trait attributes!
-- Should this instead refer to key of trialTrait?
-- NB, given that traits are now uniquely assigned to a trial
-- (no sys traits), the trial_id could be removed, and this
-- table renamed traitInteger, perhaps.
-- 
create table trialTraitNumeric(
  trial_id   INT NOT NULL,
  trait_id   INT NOT NULL,
  min        NUMERIC,
  max        NUMERIC,
  cond       TEXT,
  PRIMARY KEY(trial_id, trait_id),
  FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE,
  FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
);


create table traitString(
  trait_id   INT NOT NULL PRIMARY KEY,
  pattern    TEXT,
  FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE
);

--
-- trialTrait
--
-- Association between trials and traits
-- Separate table because a trait could be in 0, 1, or multiple trials.
-- NB - probably no longer true, as we do not have sysTraits on the android
-- device anymore. So each trait should be uniquely associated with one trial.
-- 
-- MFK, we could possibly not need this table. Perhaps instead a trait
-- could be associated with a trial by virtue of there being a trait
-- instance referencing the trial/trait.
-- This would remove the ability, however, for a trial to be imported
-- with a specified list of traits to choose from, which may make things
-- simpler.
-- Note that at the moment users can only create trait instances from the
-- traits associated with the trial via this table. I.e. they cannot
-- choose to create a trait instance from any other system trait, this is
-- maybe not ideal.
-- We could have a field in trait table indicating ID of associated trial,
-- this would support trial specific traits, and system traits, which could
-- use a special trial id (eg -3) that cannot be for a real trial.
-- 
-- 
create table trialTrait(
  trial_id   integer,
  trait_id   integer,
  FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE,
  FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
); 


--
-- traitInstance
-- 
-- replicates are numbered within instances.
-- Alternate key should be: trait_id / dayCreated / intraDayNum / rep
-- NB dayCreated and seqNum are duplicated for multiple instances within
-- one repSet. It might be good to have a repset table (or scoreSet).
--
create table traitInstance(
  _id         integer primary key autoincrement,
  trial_id    integer,
  trait_id    integer,
  dayCreated  int,
  seqNum      int,
  sampleNum   int,
  UNIQUE (trial_id, trait_id, seqNum, sampleNum),
  FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE,
  FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
);
		
-- 
-- dataset
-- Potential replacement for traitInstance
-- dayCreated and seqNum could to to separate table (or something generic?)
-- trait_id ?
-- trial_id?
-- uniqueness?
-- 
-- 
-- create table dataset(
--   id         integer primary key autoincrement,
--   type       integer,
--   ref_id     integer,
--   genint     integer,
--   UNIQUE (trial_id, trait_id, seqNum, sampleNum),
--   FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE,
--   FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
-- );
--
-- 		
-- create table scoreSet(
--   id          integer primary key autoincrement,
--   trial_id    integer,
--   trait_id    integer,
--   dayCreated  int,
--   seqNum      int,
--   UNIQUE (trial_id, trait_id, seqNum, sampleNum),
--   FOREIGN KEY(trait_id) REFERENCES trait(id) ON DELETE CASCADE,
--   FOREIGN KEY(trial_id) REFERENCES trial(_id) ON DELETE CASCADE
-- );


-- 
-- tstore
-- For values that needs to live outside existing tables.
-- EG
-- NEXT_LOCAL_NODE_ID    <trialId>	24,000,001     (and if no record present set at starting value)
-- Actually that should perhaps better be in the trial table, but it would be nice to
-- have this table anyway for when we need to record something without forcing a db change.
--
create table tstore(
  key   int,
  ref   int,
  value,
  UNIQUE (key, ref)
);

		
		
--
-- datum
--
-- Data value for node/traitInstance:
-- saved is a boolean indicating whether current value has been uploaded successfully.
-- NOTE: unique constraint does not include timestamp. Hence there can be only value
-- for a given node/ti.
-- MFK does the unique constraint mean an index is created?  If not we probably need one..
-- Also should we use "on conflict replace" to allow overwriting?
-- Probably node_id and traitInstance_id should be not null, since sqlite allows null
-- in indexes apparently (http://www.sqlite.org/lang_createtable.html#uniqueconst).
--
create table datum(
  _id              integer primary key autoincrement,
  node_id          integer,
  traitInstance_id integer,
  timestamp        integer,
  gps_long         real,
  gps_lat          real,
  userid           text,
  saved            integer,
  value,
  UNIQUE (node_id, traitInstance_id),
  FOREIGN KEY(node_id) REFERENCES node(id) ON DELETE CASCADE ON UPDATE CASCADE,
  FOREIGN KEY(traitInstance_id) REFERENCES traitInstance(_id) ON DELETE CASCADE
 );

--
-- table deletedData ?  same format as datum? but allowing dupes (datum should probably
-- have trial_id,node_id,trait_id as a unique key).
-- NB, currently datums are never deleted from datum table (but only the most recent is
-- retrieved for any trialunit/traitinstance.
--



