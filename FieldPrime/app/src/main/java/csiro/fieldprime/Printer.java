/*
 * Printer.java
 * Michael Kirk 2015
 */

package csiro.fieldprime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import android.content.Context;

import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;

public class Printer {
	static public final int BARCODE_SUB_TEXT_HEIGHT = 17;
	static public final int GAP = 10;
	static public final int X_RES_DPI = 200;
	static public final int Y_RES_DPI = 200;
	
	Context mCtx;
	String mMac;
	static com.zebra.sdk.printer.ZebraPrinter mPrinter;
	static private Connection mConnection;
		
	
	Printer(Context ctx, String macAddr) {
		mCtx = ctx;
		mMac = macAddr;
	}
	
	public void send(String commands, DlgPrintSetup.PrintSetupState prState) {
        try {
        	if (mConnection == null) {
        		mConnection = new com.zebra.sdk.comm.BluetoothConnection(mMac);
	            mConnection.open();
	            mPrinter = com.zebra.sdk.printer.ZebraPrinterFactory.getInstance(mConnection);
        	}

            try {
                File filepath = mCtx.getFileStreamPath("TEST.LBL");
                createPrintFile(mPrinter, "TEST.LBL", zebraCommandWrapper(commands, prState));
                mPrinter.sendFileContents(filepath.getAbsolutePath());
            } catch (ConnectionException e1) {
            	Util.msg("Error sending to printer, is it on?");
            	//mConnection.close();
            	mConnection = null;
            } catch (IOException e) {
            	Util.msg("Error creating file");
            }
          
            //connection.close();
        } catch (ConnectionException e) {
            //helper.showErrorDialogOnGuiThread(e.getMessage());
        	Util.msg(e.getMessage());
        } catch (ZebraPrinterLanguageUnknownException e) {
            //helper.showErrorDialogOnGuiThread(e.getMessage());
        	Util.msg(e.getMessage());
        } finally {
            //helper.dismissLoadingDialog();
        }

	}
	
	private Result createPrintFile(ZebraPrinter printer, String fileName, String pdata) throws IOException {
        FileOutputStream os = mCtx.openFileOutput(fileName, Context.MODE_PRIVATE);
        byte[] dataBytes = null;
        if (printer.getPrinterControlLanguage() == PrinterLanguage.CPCL) {
        	dataBytes = pdata.getBytes();
        } else {
        	return new Result(false, "Printer language not set to CPCL");
        }
        os.write(dataBytes);
        os.flush();
        os.close();
        return new Result();
	}
	
	/*
	 * zebraCommandWrapper()
	 * Makes printer command string out of given commands, by wrapping them
	 * with a Command Start Line, and adding form feed and print at the end.
	 * NB, dpi is hard coded as 200 (both dimensions) and count as 1.
	 * MFK parameterize everything here, and the params need to be set in DlgPrintSetup.java.
	 * Presumably goes into pstate?
	 */
	private String zebraCommandWrapper(String commands, DlgPrintSetup.PrintSetupState prState) {
		String out = String.format(Locale.US, "! %d %d %d %d 1\r\n",
				prState.mOffset, X_RES_DPI, Y_RES_DPI, prState.mLabelHeight);
		
		/*
		 *  Turn Barcode Text feature on (means human readable text are printed below barcodes):
		 *  NB font 7:0 has height of 12 dots, so plus 5 for offset makes 17. You need to add
		 *  this to the size of the barcode proper to find the total height.
		 */
		int fontNum = 7;
		int fontSize = 0;
		int bcTextOffset = 5;
		out += String.format("BARCODE-TEXT %d %d %d\r\n", fontNum, fontSize, bcTextOffset);
		
		 
		if (prState.centered())
			out += "CENTER\r\n";
		out += "GAP-SENSE\r\n";   // Default is BAR-SENSE
		out += String.format("SET-TOF %d\r\n", prState.mTopOfForm);
		out += commands;
		
		out += "FORM\r\nPRINT\r\n";
		return out;
	}
}
