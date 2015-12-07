package csiro.fieldprime;

import android.content.ContentValues;
import android.database.Cursor;
import android.view.View;

public interface SDatatype {
	void setValueFromDB(String valKey, Cursor crs);
	String getValidValueAsString();
	void writeValue(String valKey, ContentValues values); 
	View scoreView();
	void cleanup();
}
