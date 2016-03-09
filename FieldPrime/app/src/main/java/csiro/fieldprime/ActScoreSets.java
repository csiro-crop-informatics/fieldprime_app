/*
 * TIListActivity.java
 * Michael Kirk 2013
 * 
 * List of trait instances from current open trial.
 */

package csiro.fieldprime;


import org.json.JSONArray;
import org.json.JSONException;

import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;
import android.database.Cursor;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;

import static csiro.fieldprime.DbNames.*;

public class ActScoreSets extends VerticalList.VLActivity {
	private static final int REPSET_DELETE = 1;
	private static final int REPSET_STATS = 2;
	private static final int REPSET_CREATE = 3;
	private static final int REPSET_STICKY = 4;
	private static final int REPSET_GRAPHS = 5;
	
	private static final boolean webway = false;
	private boolean mDirty = false;
	
	RepSet [] mRepSets;
	TextView mStats;
	WebView mBrowser;
	Button mGraphButton;
	ButtonBar mButtonBar;
	
	@Override
	Object[] objectArray() {
		return mRepSets;
	}

	@Override
	String heading() {
		return "Trait Score Sets";
	}
	
	@Override
	void listSelect(int index) {
		if (webway)
			NEW_ListSelect(index);
		else
			OLD_ListSelect(index);
	}
	public class JavaScriptInterface {
	    public JavaScriptInterface() {
	    }
	    
	    @JavascriptInterface
	    public void getStuff(String videoAddress){
//	        Intent intent = new Intent(Intent.ACTION_VIEW);
//	        intent.setDataAndType(Uri.parse(videoAddress), "video/3gpp"); 
//	        activity.startActivity(intent);
	    }
	}

	void NEW_ListSelect(int index) {
		JavaScriptInterface jsInterface = new JavaScriptInterface();
		mBrowser.addJavascriptInterface(jsInterface, "JSInterface");
		
		mBrowser.loadData(mRepSets[index].stats(), "text/html", null);
		
        final WebSettings ws = mBrowser.getSettings();
        ws.setDisplayZoomControls(true);
        ws.setJavaScriptEnabled(true);
        //ws.setPluginState(WebSettings.PluginState.ON);
        //ws.setAllowFileAccess(true);
        ws.setDomStorageEnabled(true);
        //ws.setAllowContentAccess(true);
        //ws.setAllowFileAccessFromFileURLs(true);
        //ws.setAllowUniversalAccessFromFileURLs(true);
        mBrowser.setWebViewClient(new WebViewClient());
        mBrowser.setWebChromeClient(new WebChromeClient());
        //mBrowser.addJavascriptInterface( new WebAppInterface( this ), "Android");
        
        // generate data as json:
		RepSet rs = mRepSets[index];
		JSONArray jarr = new JSONArray();
		boolean first = true;
		double valmin = 0, valmax = 0;
		for (TraitInstance ti : rs.getTraitInstanceList()) {
			Cursor ccr = null;
			try {		
				ccr = ti.getDatumCursor();  // new nodes?
				if (ccr.moveToFirst())
					do {
						// Value field - NA indicated by absence of value:
						if (!ccr.isNull(ccr.getColumnIndex(DM_VALUE)))
							try {
								double val = ccr.getDouble(ccr.getColumnIndex(DM_VALUE));
								if (first) {
									valmin = valmax = val;
								}
								if (val < valmin) valmin = val;
								if (val > valmax) valmax = val;
								jarr.put(val);
							} catch (JSONException je) {
							
							}
					} while (ccr.moveToNext());
			} finally { if (ccr != null) ccr.close(); }
		}
		
		int width = 900;
		int height = 500;
		String htm = String.format(
				"<!DOCTYPE html>\n" + 
				"<meta charset=\"utf-8\">\n" + 
				"<div class=\"chart\"></div>\n" + 
				"<script src=\"d3.min.js\"></script>\n" + 
				"<script src=\"fplib.js\"></script>\n" + 
				"<script>\n" + 
				"  var fpHistData = %s;" +
				"</script>",
				jarr.toString());
		
	    String d3hist = String.format(
	    		"   <script src=\"d3.min.js\"></script>\n" + 
	    		"    <h3>Histogram:</h3>\n" + 
	    		"    <div id=\"hist_div\" style=\"width: %dpx; height: %dpx;\"></div>\n" + 
	    		"    <script>\n" + 
	    		"        $(document).ready(function() {\n" + 
	    		"            fplib.drawHistogram(fpHistData, %f, %f, \"hist_div\", %d, %d);\n" + 
	    		"        });\n" + 
	    		"    </script>"
	    		, width, height, valmin, valmax, width, height);
	    
	    
String mainhtml = "<!DOCTYPE html>\n" + 
		"<meta charset=\"utf-8\">\n" + 
		"<style>\n" + 
		"\n" + 
		".chart div {\n" + 
		"  font: 10px sans-serif;\n" + 
		"  background-color: steelblue;\n" + 
		"  text-align: right;\n" + 
		"  padding: 3px;\n" + 
		"  margin: 1px;\n" + 
		"  color: white;\n" + 
		"}\n" + 
		"\n" + 
		"</style>\n" + 
		"<div class=\"chart\"></div>\n" + 
		"<script src=\"http://d3js.org/d3.v3.min.js\"></script>\n" + 
		"<script>\n" + 
		"\n" + 
		"var data = [4, 8, 15, 16, 23, 42];\n" + 
		"\n" + 
		"var x = d3.scale.linear()\n" + 
		"    .domain([0, d3.max(data)])\n" + 
		"    .range([0, 420]);\n" + 
		"\n" + 
		"d3.select(\".chart\")\n" + 
		"  .selectAll(\"div\")\n" + 
		"    .data(data)\n" + 
		"  .enter().append(\"div\")\n" + 
		"    .style(\"width\", function(d) { return x(d) + \"px\"; })\n" + 
		"    .text(function(d) { return d; });\n" + 
		"\n" + 
		"</script>";
     
        mBrowser.loadUrl("file:///android_asset/main.html");
		
		mButtonBar.resetButtons(new String[]{"Create", "Delete", "Graphs"},
				new int[]{REPSET_CREATE, REPSET_DELETE, REPSET_GRAPHS});
	}
	void OLD_ListSelect(int index) {
		RepSet rs = mRepSets[index];
		mStats.setText(rs.stats());
//		// If type is string add Sticky button?
//		if (rs.getTrait().getType() == Trait.Datatype.T_STRING) {
//			mButtonBar.AddButton("Sticky", REPSET_STICKY);
//		} else {
//			// remove sticky button
//		}
	}
	@Override
	public void onBackPressed() {
	   // super.onBackPressed();

	    //Intent intent = new Intent();
	    //intent.putIntegerArrayListExtra(SELECTION_LIST, selected);
	    //setResult(RESULT_OK, intent);
		
		/*
		 * Here we can return info to caller, at a minimum, pass result_ok to indicate finish.
		 */
		if (mDirty)
			setResult(RESULT_OK);
		else
			setResult(RESULT_CANCELED);
	    finish();
	}
	@Override
	void refreshData() {
		mRepSets = g.currTrial().getRepSetsArray();
	}

	@Override
	void hideKeyboard() {
		// TODO Auto-generated method stub
	}

	@Override
	View getBottomView() {
		// Add button bar
		mButtonBar = new ButtonBar(this, new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				switch (v.getId()) {
				case REPSET_DELETE: {
					if (mCurrSelection < 0) return;
					long scoreCount = 0;
					final RepSet rs = mRepSets[mCurrSelection];

					// Confirm delete:
					String confirmPrompt = (scoreCount > 0) ?
							"There are " + rs.numScores() + " scores in this score set. Delete anyway?" :
							"Delete " + rs + "?";	
					Util.Confirm("Delete Score Set", confirmPrompt, 0, new Util.ConfirmHandler() {
						@Override
						public void onYes(long context) {
							g.currTrial().DeleteRepSet(rs);
							//setResult(RESULT_OK); could do this here no in onbackpressed to have it only then
							//ActScoring.mActScoring.clearScoringList(); // Clear scoring screen in case it is displaying now deleted TIs
							mDirty = true;
							fillScreen();   // deleting from display list is tricky, so just redraw the lot
						}
						@Override
						public void onNo(long context) {
						}
					});
					break;
				}
				case REPSET_STATS:
					if (mCurrSelection < 0) return;
					Util.toast("stats " + mRepSets[mCurrSelection]);
					break;
					
				case REPSET_CREATE: {
					if (g.currTrial().GetTraitCaptionList() == null) { // Exit if no trait
						Util.msg("No existing traits found for dataset");
						break;
					}
					DlgCreateScoreSets.newInstance(
							new DlgCreateScoreSets.handler() {
								@Override
								public void onListSelect(int[] repCounts) {
									fillScreen();
								}
							});
					break;
				}
				case REPSET_GRAPHS: {
					Util.msg("graph ");
					break;
				}
				}
			}
		});
		mButtonBar.addButton("Create", REPSET_CREATE);
		mButtonBar.addButton("Delete", REPSET_DELETE);
		return mButtonBar.Layout();
	}

	@Override
	View getMidView() {
		if (webway)
			return NEW_GetMidView();
		else
			return OLD_GetMidView();
	}
	View NEW_GetMidView() {
		mBrowser = new WebView(this);
		mBrowser.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));
		return mBrowser;
		/*
		 *  Add text pane. This is a text view inside a scrollview. Both set to
		 *  black background. Scrollview given same weight as the list, and zero size,
		 *  so they both should have half the available screen (after the other views).
		 */
//		mStats = new TextView(this);
//		Util.SetTextViewColours(mStats);
//		mStats.setTextSize(Globals.cSmallTextSize);
//		mStats.setScroller(new Scroller(this));
//		mStats.setVerticalScrollBarEnabled(true);
//		ScrollView sv = new ScrollView(this);
//		sv.setBackgroundResource(R.color.black);  // in case the text view is smaller than scrollview
//		sv.addView(mStats);
//		return sv;  // layout params?
	}
	View OLD_GetMidView() {
		/*
		 *  Add text pane. This is a text view inside a scrollview. Both set to
		 *  black background. Scrollview given same weight as the list, and zero size,
		 *  so they both should have half the available screen (after the other views).
		 */
		mStats = new TextView(this);
		Util.setColoursWhiteOnBlack(mStats);
		mStats.setTextSize(Globals.cSmallTextSize);
		mStats.setScroller(new Scroller(this));
		mStats.setVerticalScrollBarEnabled(true);
		ScrollView sv = new ScrollView(this);
		sv.setBackgroundResource(R.color.black);  // in case the text view is smaller than scrollview
		sv.addView(mStats);
		return sv;  // layout params?
	}
}
