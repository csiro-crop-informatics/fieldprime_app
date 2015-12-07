package csiro.fieldprime;

import java.util.Locale;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;

public class GPSListener implements LocationListener {
	private static LocationManager mLocManager;
	private static LocationListener mLocListener;
	private static Location mLastLocation;
	private static double mLastLongitude;
	private static double mLastLatitude;
	private static long mTimeOfFix;
	private static boolean mGpsDisabled;

	static double getLongitude() {
		return mLastLongitude;
	}
	static double getLatitude() {
		return mLastLatitude;
	}
	static Location getLocation() {
		return mLastLocation;
	}
	static boolean GPSDisabled() {
		return mGpsDisabled;
	}
	static String gpsLocationString(double lat, double lon) {
		return String.format(Locale.US, "%9.6f%s %9.6f%s", lat, lat > 0 ? "N" : "S", lon, lon > 0 ? "E" : "W");
	}
	static String getLastLocationString() {
		return gpsLocationString(mLastLatitude, mLastLongitude);
	}
	static String getLastTimeOfFix() {
		return Util.timestamp2String(mTimeOfFix);
	}
	
	public GPSListener(Context ctx) {
		mLocManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
		mLocListener = this;
		if (mLocManager != null)
			// maybe this shouldn't be called here, we could require user to call resumeUpdates.
			resumeUpdates();
		else {
			Toast.makeText(ctx, "No Location Service found", Toast.LENGTH_SHORT).show();//Util.toast();
			mGpsDisabled = true;
		}
	}

	/*
	 * pauseUpdates()
	 * It may be that battery is used up while the app is not being used if
	 * the listener is active. This may prevent this.
	 */
	static public void pauseUpdates() {  // need something like this, or have loc man and listener in scoring activity only
		// and close on pause, restart on resume?
		if (mLocManager != null)
			mLocManager.removeUpdates(mLocListener);
	}
	static public void resumeUpdates() {
		if (mLocManager != null)
			mLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, mLocListener);
	}
	
	@Override
	public void onLocationChanged(Location loc) {
		double lat = loc.getLatitude();
		double longi = loc.getLongitude();
		mTimeOfFix = loc.getTime();

		if (lat != mLastLatitude || longi != mLastLongitude) {
			//Text += " delta " + (lat - lastLatitude) + "," + (longi - lastLongitude);
			mLastLatitude = lat;
			mLastLongitude = longi;
			mLastLocation = loc;
		}
	}

	@Override
	public void onProviderDisabled(String provider) {
		mGpsDisabled = true;
		Util.toast("Gps Disabled");
		// Set last vals to zero, so it's obvious they're not valid.
		// Or perhaps we shouldn't, as last values may be informative?
		mLastLongitude = 0;
		mLastLatitude = 0;
	}

	@Override
	public void onProviderEnabled(String provider) {
		mGpsDisabled = false;
		Util.msg("Gps Enabled");
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}
}
