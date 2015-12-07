package csiro.fieldprime;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;
import android.view.View;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;
import static csiro.fieldprime.Trait.Datatype.*;

public class TraitLocation extends Trait {

	@Override
	public Datatype getType() {
		return T_LOCATION;
	}

	@Override
	Datum CreateDatum(TraitInstance ti, Node tu) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	boolean WriteDatumJSON(JSONObject jdatum, Cursor csr) throws JSONException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String repSetStats(RepSet repSet) {
		// TODO Auto-generated method stub
		return null;
	}

	class DatumLocation extends Datum {
		public DatumLocation(TraitInstance ti, Node tu) {
			super(ti, tu);
		}

		Location mLoc;
		
		@Override
		protected void WriteValue(String valKey, ContentValues values) {
			// TODO Auto-generated method stub
			// need a blob field in datum, no because it's sqlite!
		}

		@Override
		protected String GetValidValueAsString() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected void SetValueFromDB(String valKey, Cursor crs) {
			// TODO Auto-generated method stub
			
		}

		@Override
		protected View scoreView() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected void confirmValue() {
			// TODO Auto-generated method stub
			
		}

		@Override
		protected void drawSavedScore() {}

		@Override
		protected void cleanup() {}
		
	}

	@Override
	Result initTraitJSON(JSONObject jtrt, Trial trl, boolean update) throws JSONException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void SetFromDB(Trial trl) {
		// TODO Auto-generated method stub
		
	}

	@Override
	boolean SupportsBluetoothScoring() {
		// TODO Auto-generated method stub
		return false;
	}
}
