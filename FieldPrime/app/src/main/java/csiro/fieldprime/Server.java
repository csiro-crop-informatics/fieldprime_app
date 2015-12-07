/*
 * Server.java
 * Michael Kirk 2013
 * Static methods to handle communications with server.
 * MFK Need to check that all communications check HTTP status in response.
 * 
 * TODO:
 * Test if we can send status message with error back from server.
 * 
 * Might be useful to see what URLs will be hit by these funcs, to see
 * what the server is sending as responses:
 * 
 * getJson:
 * . get trial list
 *   - server response with json list or jsonErrorResponse
 *   
 * . get a trial (using url returned from get trial list)
 *   - server response with json trial or jsonErrorResponse
 *   
 * . adhoc trait
 *   - server returns json or jsonErrorResponse
 *   
 * . update trial (using upload url)
 *   This is a bit of a hack, the upload url is intended for POST, but is
 *   identical to the get_trial url, which is get. Fragile.
 * 
 * uploadJSON:
 * . to trait upload url
 *   - server returns string 'success' or error message
 * . export notes to trial upload url
 *   - server returns string 'success' or error message for notes, or when the upload is of nodes
 *     it returns json(!), but that's called with uploadJSON2JSON below
 * 
 * uploadJSON2JSON:
 * . export nodes
 * 
 * httpGetFile:
 * . get categorical trait image file
 * 
 * uploadFile:
 * . serverURL + "/crashReport";
 * . photo score, to photo upload url
 */


package csiro.fieldprime;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

public class Server {
	private Server() {}   // Static functions only, don't allow instance creation.
		
	//=== PRIVATE: ============================================================================
	/*
	 * checkConnectionStatus()
	 * Checks the response code of the connection (assumed to have completed request/response).
	 * Return null if status OK, otherwise bad Result with the response message from the server.
	 * Caller is responsible for closing connection.
	 */
	static private Result checkConnectionStatus(HttpURLConnection conn) {
		try {
			int code = conn.getResponseCode(); // vars for debugging..
			String msg = conn.getResponseMessage();
			if (code != HttpURLConnection.HTTP_OK)
				return new Result(false, msg);
		} catch (Exception e) {
			String errmsg = e.getMessage();
			if (errmsg == null)
				errmsg = "Problem in checkConnectionStatus";
			return new Result(false, errmsg);
		}
		return null;
	}
	
	/*
	 * sendJSON
	 * Packages the sending of json on a connection. All exception passed through.
	 * Return null if no problems, otherwise bad Result.
	 * Caller is responsible for closing connection.
	 */
	static private Result sendJSON(HttpURLConnection conn, JSONObject json, String baseUrl) throws Exception {
		String jsonString = json.toString();
		//conn.setRequestProperty( "Accept-Encoding", "" );
		conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		conn.setDoOutput(true);  // NB this sets method to POST
		conn.setFixedLengthStreamingMode(jsonString.length());
		conn.connect();
		OutputStream out = conn.getOutputStream();
		out.write(jsonString.getBytes());
		out.flush();
		out.close();
		Result statusCheck = checkConnectionStatus(conn);
		if (statusCheck != null)
			return statusCheck;
		return null;
	}
	
	/*
	 * checkJSONError()
	 * If passed object has "error" key, the value is returned, else null.
	 * It shouldn't really throw an exception, because we check first..
	 */
	static private String checkJSONError(JSONObject jobj) throws JSONException {
		if (jobj.has("error")) {
			return jobj.getString("error");
		}
		return null;
	}

	/*
	 * readJSON()
	 * Gets response, assumed to be JSON from connection. Returns Result, containing JSON object if successful.
	 * NB, it is expected that this is called from an asynchronous thread (not the UI thread).
	 */
	static private Result readJSON(HttpURLConnection conn) throws Exception {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String json = "";
			String line = null;
			while ((line = reader.readLine()) != null) {
				json += line;
			}
			JSONObject responseJson = new JSONObject(json);
			String jerr = checkJSONError(responseJson); // Check for error reported by server-side
			if (jerr != null) {
				return new Result(false, jerr);
			}
			return new Result(responseJson);
		} catch (Exception e) {
			return new Result(false, e.getMessage());
		} finally {
			if (reader != null)
				reader.close();
		}
	}
	
	private static HttpURLConnection getConnection(String baseUrl, boolean addStandardParams)
			throws MalformedURLException, IOException {
		//System.setProperty("http.keepAlive", "false");
		if (addStandardParams)
			baseUrl = addStandardOutOfBandParams(baseUrl);
		URL url = new URL(baseUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setUseCaches(false); 
		conn.setConnectTimeout(8000);
		conn.setRequestProperty("connection", "close");   // stops bug where second connection fails.
		return conn;
	}
	
	/*
	 * copyInputStreamToFile()
	 * NB - this function closes the input stream.
	 */
	private static Result copyInputStreamToFile(InputStream in, File file ) {
		OutputStream out = null;
	    try {
	        out = new FileOutputStream(file);
	        byte[] buf = new byte[1048576];
	        int len;
	        while ((len=in.read(buf)) > 0)
	            out.write(buf,0,len);
	    } catch (Exception e) {
			return new Result(false, e.getMessage());
	    } finally {
	    	try {
		    	if (in != null) in.close();
		    	if (out != null) out.close();
	    	} catch (Exception e) {}
	    }
	    return new Result();
	}


	//=== PUBLIC: ==============================================================================

	/*
	 * uploadJSON()
	 * Upload single json object to given url with HTTP POST.
	 * Standard out-of-band parameters are added to the url.
	 * NB, this should be called from an asynchronous thread, not the UI thread.
	 * 
	 * Perhaps the protocol should be that a json response is expected, and then
	 * returned in the result - with json error codes being extracted.
	 * Current situation is the server indicates success by simply returning 'success'
	 * as the (first line of the) response body.
	 */
	static Result uploadJSON(JSONObject json, String baseUrl) {
		HttpURLConnection conn = null;
		try {
			conn = getConnection(baseUrl, true);
			Result res = sendJSON(conn, json, baseUrl);
			if (res != null)
				return res;

			/*
			 * MFK - here we have a problem:
			 * Current situation is that server returns simply "success" on success, and we return
			 * error from this func if we don't get it. I would like to replace this by a JSON
			 * return with more detail. But once we change this response on the server, older clients
			 * will return fail from this func, even when upload was successful. 
			 * Solution: change code here to accept either "success" or new format. But server must
			 * keep sending "success". Eventually when all older clients are no longer used, can change server,
			 * and the old method from this func. Alternatively use client version specific processing
			 * on the server.
			 */
			
			// old way:
			// We don't need this if server return HTTP error code on failure. But we do need it
			// while server returns (as it does currently) the OK code, indicating failure by lack
			// of 'success' return body.
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String tmp = reader.readLine();
			String errMsg = "";
			if (!tmp.equalsIgnoreCase("success")) { // Concatenate to err message but carry on?
				do {
					errMsg += tmp + "\n";
				} while ((tmp = reader.readLine()) != null);
				return new Result(false, errMsg);
			}
		} catch (Exception e) {
			return new Result(false, "Error:" + e.getMessage());
		} finally {
			if (conn != null)
				conn.disconnect();
		}
		return new Result();
	}
	
	/*
	 * uploadJSON2JSON()
	 * Upload single json object to given url (HTTP POST).
	 * Standard out-of-band parameters are added to the url.
	 * 
	 * This version returns json response.
	 * 
	 * NB, it is expected that this is called from an asynchronous thread (not the UI thread).
	 */
	static Result uploadJSON2JSON(JSONObject json, String baseUrl) {
		HttpURLConnection conn = null;
		try {
			conn = getConnection(baseUrl, true);
			Result res = sendJSON(conn, json, baseUrl);
			if (res != null)
				return res;
			return readJSON(conn);
		} catch (Exception e) {
			return new Result(false, "Error:" + e.getMessage());
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}

	/*
	 * getJSON()
	 * HTTP GET a JSON response for the specified URL, with the parameters indicated by keys and vals.
	 * Pass null keys to indicate no parameters.
	 * NB if parameters are provided, the url is assumed to have no parameters on entry.
	 */	
	static Result getJSON(String baseUrl, String[] keys, String[] vals) {
		HttpURLConnection conn = null;
		try {
			String urlWithParams = addStandardOutOfBandParams(baseUrl);
			if (keys != null) {
				addParametersToUrl(urlWithParams, keys, vals);
			}
			conn = getConnection(urlWithParams, false);
			conn.setDoInput(true);
			conn.connect();			// Do we need to call conn.connect() to initiate connection?
			
			// Check status:
			Result statusCheck = checkConnectionStatus(conn);
			if (statusCheck != null)
				return statusCheck;
			
			// Get and return json:
			return readJSON(conn);
		} catch (Exception e) {
			return new Result(false, "Error:" + e.getMessage());
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}
	static Result getJSON(String url) { return getJSON(url, null, null); }
			
	/*
	 * httpGetFile()
	 * Request url and store returned data in specified filename.
	 * It is assumed that destFileName is available to be written
	 * (eg all parent dirs in place).
	 * 
	 * NB, it is expected that this is called from an asynchronous thread (not the UI thread).
	 */
	static Result httpGetFile(String sUrl, String destFileName) {
		HttpURLConnection conn = null;
		try {
			sUrl = sUrl.replace(" ", "%20");   // better way for this ?
			/* I think we need to remove spaces because in the current single call of this func
			 * from TraitCategorical the URL is one from the server, but it contains a file name
			 * originally provided by a user (defining the categorical trait). We probably should
			 * rename the file when loading them to the server.. 
			 */
			conn = getConnection(sUrl, false);
			conn.connect();
			Result res = copyInputStreamToFile(conn.getInputStream(), new File(destFileName));
			return res;
		} catch (Exception e) {
			StackTraceElement ste = e.getStackTrace()[0];
			return new Result(false, "HttpGetFile: exception in " +
					ste.getMethodName() + ":" + ste.getLineNumber() + ": " + e.getMessage());
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}

	/*
	 * addParametersToUrl()
	 * Add arbitrary set of URL "?" parameters.
	 * Copes regardless of whether there are parameters already in the url.
	 */
	static String addParametersToUrl(String url, String[] keys, String[] vals) {
		boolean firstParam;
		if (keys == null) return url;
		if (!url.contains("?")) {
			url += "?";
			firstParam = true;
		} else {
			firstParam = url.endsWith("?");
		}
			
		for (int i = 0; i < keys.length; ++i) {
			if (!firstParam) {
				url += "&";
			} else firstParam = false;
			try {
				url += keys[i] + "=" + URLEncoder.encode(vals[i], "UTF-8");
			} catch (UnsupportedEncodingException e) {
				// MFK, not expected
				e.printStackTrace();
			}
		}
	    return url;
	}


	/*
	 * addStandardOutOfBandParams()
	 * Adds "?" parameters to URL.
	 * Note that although we are trying to use HATEOAS as much as possible, there is some data
	 * that resides on the client, that needs to be provided with the URL (i.e. cannot be provided
	 * by the server in pre prepared URLs). In particular there is the data access password, the client
	 * software version, and the device android id.
	 */
	static String addStandardOutOfBandParams(String in) {
		return addParametersToUrl(in, new String [] {"pw", "ver", "andid"},
				new String [] {Globals.g.prefs().GetServerPassword(), Util.getVersionCode(), Globals.g.AndroidID()});
	}

	/*
	 * getTrialList()
	 * Returns result with the trial list retrieved from server.
	 * This uses the server cool URL, user and password from
	 * the configured preferences. 
	 * NB, this should be the only place the configured server URL is accessed.
	 * Every server access subsequent to this should be using a URL provided by
	 * the server (HATEOAS style).
	 * NB Asychronous.
	 */
	static Result getTrialList() {
		Context ctx = Globals.g.getCurrentActivity();
		String serverURL = Globals.g.prefs().GetServerURL();
		String suser = Globals.g.prefs().GetServerUser();
		// Check we have a server and user configured:
		if (serverURL.equals("")) {
			return new Result(false, ctx.getString(R.string.server_address_title) + " not configured in Preferences");
		}
		if (suser.equals("")) {
			return new Result(false, ctx.getString(R.string.server_user_title) + " not configured in Preferences");
		}		
		String url = serverURL + "/user/" + suser + '/';
		return Server.getJSON(url);
	}
	
	/*
	 * uploadFile()
	 * HTTP upload of specified local file, to specified server, with specified name set.
	 */
	static Result uploadFile(String urlServer, String pathToOurFile, String nameToUse) {
		HttpURLConnection connection = null;
		DataOutputStream outputStream = null;
		FileInputStream fileInputStream = null;

		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = "*****";

		int bytesRead, bytesAvailable, bufferSize;
		byte[] buffer;
		int maxBufferSize = 1 * 1024 * 1024;
		try {
			fileInputStream = new FileInputStream(new File(pathToOurFile));
			connection = getConnection(urlServer, false);

			// Allow Inputs & Outputs
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);

			// Enable POST method
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Connection", "Keep-Alive");
			connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

			outputStream = new DataOutputStream(connection.getOutputStream());
			outputStream.writeBytes(twoHyphens + boundary + lineEnd);
			
			String toWrite = "Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + nameToUse + "\"" + lineEnd;
			outputStream.writeBytes(toWrite);
			outputStream.writeBytes(lineEnd);

			bytesAvailable = fileInputStream.available();
			bufferSize = Math.min(bytesAvailable, maxBufferSize);
			buffer = new byte[bufferSize];

			// Read file
			bytesRead = fileInputStream.read(buffer, 0, bufferSize);
			while (bytesRead > 0) {
				outputStream.write(buffer, 0, bufferSize);
				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				bytesRead = fileInputStream.read(buffer, 0, bufferSize);
			}

			outputStream.writeBytes(lineEnd);
			outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
			outputStream.flush();
			
			// Check response OK:
			Result statusCheck = checkConnectionStatus(connection);
			if (statusCheck != null)
				return statusCheck;
			else
				return new Result();
		} catch (Exception ex) {
			return new Result(false, ex.getMessage());
		} finally {
			try {
				if (fileInputStream != null) fileInputStream.close();
				if (outputStream != null) outputStream.close();
			} catch (Exception e) {}
		}
	}
	

//	static private Result getJSONResponse(HttpResponse response) {
//		try {
//			BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
//			String json = "";
//			String line = null;
//			while ((line = reader.readLine()) != null) {
//				json += line;
//			}
//			
//			JSONObject responseJson = new JSONObject(json);
//			// Check for error reported by server-side:
//			String jerr = GetJSONError(responseJson);
//			if (jerr != null) {
//				return new Result(false, jerr);
//			}
//			return new Result(responseJson);
//		} catch (Exception e) {
//			return new Result(false, e.getMessage());
//		}
//	}

	
//	/*
//	 * ServerResponseInterpretation()
//	 * Returns null if server response is Accepted or OK, otherwise a string.
//	 */
//	static private String serverResponseInterpretation(HttpResponse httpResponse) {
//		switch (httpResponse.getStatusLine().getStatusCode()) {
//		case HttpStatus.SC_ACCEPTED:
//		case HttpStatus.SC_OK:
//			break;
//		case HttpStatus.SC_NOT_FOUND:
//			return "server returned NOT FOUND";
//		default:
//			return "Error accessing server: " + httpResponse.getStatusLine().getReasonPhrase();
//		}
//		return null;
//	}

	
}
