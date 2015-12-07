/*
 * AboutActivity.java
 * Michael Kirk 2013
 * 
 * About information screen.
 */
package csiro.fieldprime;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class ActAbout extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		WebView webview = new WebView(this);
		String html = "<html><body><h1>Field Prime</h1>";
		String version = Util.getVersionName() + " (Build " + Util.getVersionCode() + ")";
		html += "<h2>Beta Version: " + version + "</h2>";
		html += "FieldPrime is made by the CSIRO Plant Industry Bioinformatics group";
		html += "<p><b>Architect/Author:</b> Michael Kirk.";
		html += "<p><b>Specification, Ideas, and Testing:</b><br>"
				+ "&nbsp;Alexandre Boyer<br>"
				+ "&nbsp;Colin Cavanagh<br>"
				+ "&nbsp;Kathy Dibley<br>"
				+ "&nbsp;Chris Herrman<br>"
				+ "&nbsp;Michael Kirk<br>"
				+ "&nbsp;Aswin Singaram Natarajan<br>"
				+ "&nbsp;Jen Taylor<br>"
				;
		html += "<p>This software is not for distribution or use outside of CSIRO without permission";
		
		// Show GPS position, if available:
		html += GPSListener.GPSDisabled() ? "<h3>GPS currently disabled</h3>" :
			String.format("<h3>GPS position</h3>%s<br>At time %s",
					GPSListener.getLastLocationString(),
					GPSListener.getLastTimeOfFix());
		
		html += "</body></html>";
		webview.loadData(html, "text/html", null);
		setContentView(webview);
	}
}
