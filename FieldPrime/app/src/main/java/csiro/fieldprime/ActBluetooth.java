/*
 * ActBluetooth.java
 * Michael Kirk 2013
 *
 * Bluetooth activity.
 * Allows user to search for and connect to bluetooth device.
 * Show list of paired devices, with scan button to optionally find
 * unpaired devices. On selection of device (paired or not), create dialog
 * for user to specify whether the device is to be used for navigation or
 * score entry, and if for score entry, which - if any - traits it should
 * be restricted to.
 *
 * UI allows the user to specify for each connection whether it is used for:
 * Navigation
 * Scoring for a specific trait
 * Scoring for any trait.
 *
 * ToDo
 * Save MyBluetoothDevices against hardware device ids, remake open connections
 * after trial/app close and reopen.
 * Maybe timer to keep trying to connect?
 */

package csiro.fieldprime;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import csiro.fieldprime.Trial.NodeAttribute;

import static csiro.fieldprime.ActBluetooth.Mode.*;


public class ActBluetooth extends VerticalList.VLActivity {
	public enum Mode { NONE, NAVIGATION, SCORING }
	public enum Cstate {UNCONNECTED, CONNECTING, CONNECTED }
	public enum DeviceType {
		AUTO_DETECT(null),
		CUSTOM(null),
		MOTOROLA_CS300("(.*)\r"),
		METTLER_TOLEDO_ML3002("(.*)\033enter."),
		METTLER_TOLEDO_ML4001("\\s*(.*) g\\s*"),
		DECIMAL_IN_STRING("\\D*(\\d+\\.\\d+)\\D*"),
		ALLFLEX(".. (.*)\r\n"),
		ZEBRA_PRINTER(null)
		;
		private String mPatternString = null;
		private Pattern mPattern = null;
		public String getPatternString() {return mPatternString;}
		public Pattern getPattern() {return mPattern;}
		private DeviceType(String patternString) {
			mPatternString = patternString;
			if (patternString != null)
				mPattern = Pattern.compile(patternString);
		}
	};
	interface handlers {
		void handleBluetoothValue(String value, MyBluetoothDevice btDev);
    }
	static private final String SCAN_LABEL = "Scan";
	static private final String CONNECT_LABEL = "Connect";
	static private final String DISCONNECT_LABEL = "Disconnect";
	static private final String CONFIG_LABEL = "Configure";
	static private final int SCAN = 1;
	static private final int CONNECT_TOGGLE = 2;
	static private final int CONFIGURE = 3;
	static private final byte  mMTEnd[] = new byte[] {27,101,110,116,101,114,46};   // bytes at end of Mettler Toledo scale send

	private BluetoothAdapter mBluetoothAdapter = null;
	private ArrayList<MyBluetoothDevice> mDeviceList = new ArrayList<MyBluetoothDevice>();
	private TextView mText;
	private ButtonBar mButtonBar;
	private Button mConnectButton;
	private Button mConfigButton;

	// Bluetooth on/off, not used yet.
    private void onBluetooth() {
        if (!mBluetoothAdapter.isEnabled())
            mBluetoothAdapter.enable();
    }
    private void offBluetooth() {
        if (mBluetoothAdapter.isEnabled())
            mBluetoothAdapter.disable();
    }

	private void getPairedDevices() {
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				boolean present = false;
				for (MyBluetoothDevice d : mDeviceList) {
					if (d.device().equals(device)) {
						present = true;
						break;
					}
				}
				if (!present)
					mDeviceList.add(new MyBluetoothDevice(device));
			}
		}
	}

	private void scan() {
		Toast.makeText(getBaseContext(),
				"Searching for devices, make sure your device is discoverable", Toast.LENGTH_SHORT).show();
		BroadcastReceiver myReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (BluetoothDevice.ACTION_FOUND.equals(action)){
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					// Check not already in list
					for (MyBluetoothDevice d : mDeviceList) {
						if (device.getAddress().equals(d.device().getAddress()))
							return;
					}
					mDeviceList.add(new MyBluetoothDevice(device));
					//FillScreen();  wait till discovery finished?
				} else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
					Util.toast("Discovery finished");
					ActBluetooth.this.unregisterReceiver(this);
					fillScreen();
				}
			}
		};
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
		intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		ActBluetooth.this.registerReceiver(myReceiver, intentFilter);
		mBluetoothAdapter.startDiscovery();
	}

	private void updateButtonsAndDetails() {
		mButtonBar.removeAll();
		mButtonBar.addButton(SCAN_LABEL, SCAN);
		if (mDeviceList == null) return;  // shouldn't happen
		if (mCurrSelection < 0) return;
		MyBluetoothDevice dev = mDeviceList.get(mCurrSelection);
		if (dev == null) return;

		// If there's a device selected, show config button:
		mConfigButton = mButtonBar.addButton(CONFIG_LABEL, CONFIGURE);
		// If' it has configuration, show (dis)connect):
		if (dev.getMode() != Mode.NONE)
			mConnectButton = mButtonBar.addButton(dev.connectState() == Cstate.CONNECTED ? DISCONNECT_LABEL : CONNECT_LABEL,
					CONNECT_TOGGLE);

		mText.setText(dev.details());
	}

	private handlers getHandler() {
		Activity curr = g.getCurrentActivity();
		if (curr instanceof handlers)
			return (handlers)curr;
		else
			return null;
	}

	View.OnClickListener buttonHandler = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
				case SCAN:
					scan();   // MFK how about disabling stuff while scanning?
					break;
				case CONNECT_TOGGLE:
					// may be connect or disconnect, check isConnected() (which should be kept accurate in all cases)
					if (mCurrSelection < 0) break;
					MyBluetoothDevice dev = mDeviceList.get(mCurrSelection);
					if (dev.connectState() == Cstate.UNCONNECTED)
						dev.asyncRunDevice();
					else
						dev.disconnect();
					updateButtonsAndDetails();
					break;
				case CONFIGURE:
					mDeviceList.get(mCurrSelection).configure();
					break;
			}
		}
	};

	/*** VerticalList Overrides: ***************************************************************************/

	@Override
	public void refreshData() {
		if (mBluetoothAdapter == null)
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// Paired device list:
		mDeviceList = g.currTrial().mBtDeviceList;
		if (mDeviceList == null) {
			mDeviceList = new ArrayList<MyBluetoothDevice>();
			g.currTrial().mBtDeviceList = mDeviceList;
		}
		getPairedDevices(); // Populate the list with already paired devices
	}

	/*
	 * ObjectArray()
	 * Assumes mDeviceList is not null (i.e. RefreshData has been called).
	 */
	@Override
	Object[] objectArray() {
		return mDeviceList.toArray();
	}


	@Override
	String heading() {
		return "Bluetooth Devices";
	}

	@Override
	void listSelect(int index) {
		updateButtonsAndDetails();
	}
		
	@Override
	View getMidView() {
		if (mText == null)
			mText = super.makeTextView();
		return mText; 
	}

	@Override
	View getBottomView() {
		if (mButtonBar == null)
			mButtonBar = new ButtonBar(this, buttonHandler);
		updateButtonsAndDetails();
		return mButtonBar.Layout();
	}
	
	@Override
	void hideKeyboard() {}


	/*** class MyBluetoothDevice: ************************************************************************************
     *
     * Bluetooth device with some extra stuff. (Note extending BluetoothDevice is not possible, it seems).
     * These are used in an ArrayAdapter, and we use toString to define presentation string in the list.
     * Each device may have an associated trait (mTrait), if this is set, then the scores from the
     * device should only be used for the specified trait.
     * If isNavigator() then values from this device should be interpreted as plot barcodes.
     */
    class MyBluetoothDevice {
      	private BluetoothDevice mDevice;
		private Mode mMode = NONE;
      	//private boolean mNavigator = true;
		private Trial.NodeAttribute mNavAttribute;
      	private Trait mTrait;
      	private Cstate mConnected = Cstate.UNCONNECTED;
		private BluetoothConnection mBtConnection;
		public Pattern mPattern;
		public DeviceType mDeviceType = DeviceType.AUTO_DETECT;
      	
      	MyBluetoothDevice(BluetoothDevice btd) {
      		mDevice = btd;
    	}

    	public String toString() {
    		String bondState = "Unknown State";
    		switch (mDevice.getBondState()) {
    		case BluetoothDevice.BOND_NONE:
    			bondState = "Not Paired";
    			break;
    		case BluetoothDevice.BOND_BONDING:
    			bondState = "Bonding";
    			break;
    		case BluetoothDevice.BOND_BONDED:
    			bondState = "Paired";
    			break;
    		}
    		String config = mConnected.toString();
			switch (mConnected) {
				case UNCONNECTED:
					config = "Not connected";
					break;
				case CONNECTING:
					config = "Trying to connect";
					break;
				case CONNECTED:
					config = "Connected";
					if (getMode() == NAVIGATION) config += " for navigation";
					else if (mTrait == null) config += " for scoring any trait";
					else config += " for scoring trait " + mTrait.getCaption();
					break;
			}
    		return mDevice.getName() + "\n" + mDevice.getAddress() + " " + bondState + " " + config;
    	}
    	public BluetoothDevice device() { return mDevice; }
		public String getName() { return mDevice.getName();}

    	public Trait getTrait() {
			return (getMode() == SCORING) ? mTrait : null;
		}
    	public void setTrait(Trait t) {
			mTrait = t;
			mNavAttribute = null;
		}
    	public void setMode(Mode mode) {
			mMode = mode;
		}
		public Mode getMode() {
			return mMode;
		}

		public void setNavAttribute(NodeAttribute att) {
			mNavAttribute = att;
			mTrait = null;
		}
		public NodeAttribute getNavAttribute() {
			return getMode() == NAVIGATION ? mNavAttribute : null;
		}

    	public void setConnected(boolean connected) {
			mConnected = connected ? Cstate.CONNECTED : Cstate.UNCONNECTED;
		}
    	public Cstate connectState() {
			return mConnected;
		}

		public CharSequence details() {
			String details = toString();
			switch (mMode) {
				case NONE:
					break;
				case NAVIGATION:
					details += "\nUse for navigation";
					NodeAttribute natt = getNavAttribute();
					if (natt != null)
						details += " using attribute " + natt.name();
					break;
				case SCORING:
					details += "\nUse for scoring";
					Trait trt = getTrait();
					if (trt != null)
						details += " using trait " + trt.getCaption();
					break;
			}

			return details;
		}

		private void determineDeviceType() {
			// NB. I don't think these device classes are unique to the
			// device types here, so this must be overridable by user.
			// For example 7936 (used by motorola) is in the Android
			// BlueTooth docs as "UNCATEGORIZED". And zero is MISC.
			// I don't have a Mettler Toledo scale to find out what it's class is.
			int devClass = device().getBluetoothClass().getDeviceClass();
			Util.toast(String.format("BlueTooth Device Class: %d", devClass));
			switch (devClass) {
				case 1664:
					mDeviceType = DeviceType.ZEBRA_PRINTER;
					break;
				case 7936:  // Mettler Toledo and CS300 both have this
//					mDeviceType = DeviceType.MOTOROLA_CS300;
					mDeviceType = DeviceType.AUTO_DETECT;
					break;
				case 0:
					mDeviceType = DeviceType.ALLFLEX;
					break;
				default:
					mDeviceType = DeviceType.AUTO_DETECT;
			}
		}
		private boolean isPrinter(BluetoothDevice btd) {
			return (btd.getBluetoothClass().getDeviceClass() == 1664);
		}

		private void configure() {
			determineDeviceType();
			if (mDeviceType == DeviceType.ZEBRA_PRINTER) {
				device().getAddress();
				// Get and remember the mac address, have to get this back to the scoring activity
				g.setPrinterMacAddr(device().getAddress());
				Util.msg("Set as current printer");
				return;
			}
			DlgBTconnect.newInstance(this);
		}

		void disconnect() {
			if (mBtConnection != null)
				mBtConnection.myCancel();
			setConnected(false);  // redundant but harmless
		}

		public void asyncRunDevice() {
			switch (connectState()) {
				case UNCONNECTED:
					Util.toast("Trying to connect to " + mDevice.getName());
					mBtConnection = new BluetoothConnection();
					/*
					 * MFK This use of mActTrial should be replaced, since in some circumstances
					 * mActTrial will be null.
					 */
						ActTrial.mActTrial.addBluetoothConnection(mBtConnection);
					/*
					 * NB we may have multiple bluetooths working so we need them to be able to
					 * multitask - hence the call to executeOnExecutor() rather than just execute().
					 */
					mBtConnection.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new MyBluetoothDevice[]{this});
					break;
				case CONNECTING:
					Util.toast("Already trying to connect to " + mDevice.getName());
					break;
				case CONNECTED:
					Util.toast("Already connected to " + mDevice.getName());
					break;
			}
		}
	}

	/*** class BluetoothConnection: ***********************************************************************************
	 *
	 * AsyncTask for handling communication with single bluetooth device.
     * NB, to stop the thread call myCancel().
     */
    class BluetoothConnection extends AsyncTask<MyBluetoothDevice, String, Void> {
		static private final String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";   // standard uuid
    	MyBluetoothDevice mDevice;
    	private Exception mException;
        private InputStream mStream;

    	@Override
    	protected Void doInBackground(MyBluetoothDevice... params) {
    		mDevice = params[0];
    		BluetoothDevice device = mDevice.device();
    		try {
    			//BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
    			BluetoothSocket socket = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
    			socket.connect();
    			publishProgress("xxx");  // flag successful connection
    			mStream = socket.getInputStream();
    			int red = 0;
    			int curLen = 0;
				final int BUFFER_LENGTH = 256;
    			byte[] buffer = new byte[BUFFER_LENGTH];
    			do {
    				try {
    					red = mStream.read(buffer, curLen, BUFFER_LENGTH - curLen);
						if (red == -1) {
							Util.toast("End of stream reached");  // MFK remove, also can we detect if connection lost?
						}
						curLen += red;

						if (mStream.available() > 0) continue;
						String data = new String(buffer, 0, curLen);

						if (mDevice.mDeviceType == DeviceType.AUTO_DETECT) {
							// Try various options (for which we have mutually exclusive patterns):
							Matcher matcher = DeviceType.MOTOROLA_CS300.getPattern().matcher(data);
							if (!matcher.matches())
								matcher = DeviceType.METTLER_TOLEDO_ML3002.getPattern().matcher(data);
							if (!matcher.matches())
								matcher = DeviceType.ALLFLEX.getPattern().matcher(data);
							if (!matcher.matches())
								matcher = DeviceType.METTLER_TOLEDO_ML4001.getPattern().matcher(data);
							if (matcher.matches()) {
								data = matcher.group(1);
							}
						} else {
							// Generic processing
							// Use filter if one provided, else just process the read data.
							// do filtering, or matching here
							if (mDevice.mPattern != null) {
								Matcher matcher = mDevice.mPattern.matcher(data);
								if (matcher.matches()) {
									data = matcher.group(1);
								} else {
									data = "fpmsg: " + "Does not match pattern";
									//curLen = 0;
									//continue;
								}
							}
						}

						curLen = 0;
						publishProgress(data);
    				} catch (Exception ex) {
    					red = -1;
    				}
    			} while (red > 0 && !isCancelled());
    		} catch (IOException e) {
    			// I think we get here if no connection established, but we handle that in onProgressUpdate.
    			this.mException = e;
    			//e.printStackTrace();
    		}
    		return null;
    	}

    	/*
    	 * myCancel() 
    	 * We need this - above the asyncTask cancel functionality, because
    	 * in the main processing loop it is mostly hung waiting for input from
    	 * the bluetooth device, and while so hung it can't check for the cancelled
    	 * flag. So we need to stop the read.
    	 */
    	public void myCancel() {
    		cancel(true);
    		try {
    			mStream.close();
    		} catch (Exception ex) {
    			Util.msg("failed mStream.close");
			}
			mDevice.setConnected(false);
    	}
    	
    	@Override
    	protected void onProgressUpdate(String... values) {
    		if (values[0].equals("xxx")) {
    			mDevice.setConnected(true);
				updateButtonsAndDetails();
    			Util.toast("Connection Established");
    			fillScreen();   // We need to change the text of just connected device
    		} else if (values[0].startsWith("fpmsg:")) {
				Util.toast(values[0].substring(6));
			} else {
				String val = values[0];
				if (val.length() > 0) {
					handlers h = getHandler();
					if (h != null) {
						h.handleBluetoothValue(val, mDevice);
					}
				}
			}
    		super.onProgressUpdate(values);
    	}
    	
    	@Override
    	protected void onPostExecute(Void v) {
    		if (mException != null) {
    			Util.msg("Cannot establish connection to " + mDevice.getName());
    			Util.exceptionHandler(mException, "BluetoothConnection:onPostExecute");
    		} else {
    			Util.msg("Bluetooth listener stopped for device " + mDevice.getName());
    		}
    		mDevice.setConnected(false);
    	}
    }

	/*** class DlgBTconnect: ***********************************************************************************
	 *
	 * Dialog fragment to determine how user wishes to use the device, and then initiate the connection.
	 * MFK rather than making this robust thru restart, perhaps we can just detect and close?
	 */
	public static class DlgBTconnect extends DialogFragment {
		static private DlgBTconnect instance = null;    // only allow one Search instance at a time
		private  VerticalList mMainView;
		private  RadioGroup mChoice;
		private String mPatternString;
		private TextView mPatternStringEditPrompt;
		private EditText mPatternStringEdit;
		private TextView mDevTypeSpinnerPrompt;
		private Spinner mDevTypeSpinner;
		static private final int CHOICE_NAVIGATION = 1;
		static private final int CHOICE_SCORING = 2;
		private TextView mNavPrompt;
		private TextView mTraitPrompt;
		private Spinner mTraitSpinner, mNavSpinner;
		static private MyBluetoothDevice mDevice;

		public static void newInstance(final MyBluetoothDevice device) {
		/*
		 * we probably need to put trial id, nat id, attvalue in bundle (if they're needed)
		 * and use getActivity to manage the callbacks.
		 * Perhaps this will break under rotation - test this..
		 * Need to see if bundle parameters are re provided.
		 */
			mDevice = device;
			if (instance == null) instance = new DlgBTconnect();
			instance.show(Globals.FragMan(), "dlgBTconnect");
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			Trial trl = ((Globals)getActivity().getApplication()).currTrial();

			/*
			 * Set up vlist with attribute selection spinner, and then when an
			 * attribute is selected a spinner with that attribute's values
			 */
			mMainView = new VerticalList(getActivity(), false);
			/*
			 * Construct list of options for use of device once connected.
			 */
			mChoice = mMainView.addVerticalRadioGroup(
					new String[]{"Use for navigation", "Use for scoring"},
					new int[]{CHOICE_NAVIGATION, CHOICE_SCORING},
					new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							RadioButton rb = (RadioButton) v;
							int which = v.getId();
							// Clear/show choice specific items:
							mNavPrompt.setVisibility(which == CHOICE_NAVIGATION ? View.VISIBLE : View.GONE);
							mNavSpinner.setVisibility(which == CHOICE_NAVIGATION ? View.VISIBLE : View.GONE);
							mTraitPrompt.setVisibility(which == CHOICE_SCORING ? View.VISIBLE : View.GONE);
							mTraitSpinner.setVisibility(which == CHOICE_SCORING ? View.VISIBLE : View.GONE);
						}
					});

			// Pattern stuff - we probly want to pass in select handler for the spinner,
			// so that if CUSTOM is selected we show EditText, or maybe it's OK to just
			// have this there in all cases. But it needs a prompt. Try horiz Viewline?

			// MFK, here and in other places (DlgFilter) we have a prompt and a spinner.
			// Perhaps make a VerticalList object for this. With visibility set option
			/*
			 * Navigation extras:
			 * Note in the attribute list we put at position 0 (default) an option that
			 * is not an attribute but indicates the barcode field of the Node class.
			 * We may eventually remove that field, in which case if the user doesn't
			 * specify an attribute we just search for trait barcode attributes.
			 * MFK note currently CHOICE_ANY_TRAIT is the same as CHOICE_SCORING
			 * with not trait selected, may as well remove CHOICE_ANY_TRAIT.
			 */
			mNavPrompt = mMainView.addTextNormal("Navigation attribute:");
			mNavPrompt.setVisibility(View.GONE);
			mNavSpinner = mMainView.addSpinner(trl.getAttributes(), " Barcode (default)", mDevice.getNavAttribute());
			mNavSpinner.setPrompt("coconuts");
			mNavSpinner.setVisibility(View.GONE);

			// Scoring extras:
			mTraitPrompt = mMainView.addTextNormal("Trait to score:");
			mTraitPrompt.setVisibility(View.GONE);
			mTraitSpinner = mMainView.addSpinner(new ArrayList<Trait>(Arrays.asList(trl.getTraitList())),
					"..Use for any trait..", mDevice.getTrait());
			mTraitSpinner.setVisibility(View.GONE);

			// Set mode check if available:
			switch (mDevice.getMode()) {
				case NONE:
					break;
				case NAVIGATION:
					((RadioButton)mChoice.getChildAt(0)).setChecked(true);
					mNavPrompt.setVisibility(View.VISIBLE);
					mNavSpinner.setVisibility(View.VISIBLE);
					break;
				case SCORING:
					((RadioButton)mChoice.getChildAt(1)).setChecked(true);
					mTraitPrompt.setVisibility(View.VISIBLE);
					mTraitSpinner.setVisibility(View.VISIBLE);
					break;
			}

			mMainView.addLine();
			mDevTypeSpinnerPrompt = mMainView.addTextNormal("Device Type:");
			ArrayList<DeviceType> devTypes = new ArrayList<DeviceType>(Arrays.asList(DeviceType.values()));
			mDevTypeSpinner = mMainView.addSpinner(devTypes, null, mDevice.mDeviceType); // detect type for defaults
			mDevTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
					TextView tv = (TextView) selectedItemView;
					int vis = tv.getText() == DeviceType.CUSTOM.name() ? View.VISIBLE : View.GONE;
					mPatternStringEditPrompt.setVisibility(vis);
					mPatternStringEdit.setVisibility(vis);
					Util.toast("position: " + position + tv.getText() + " name:" + DeviceType.CUSTOM.name());
				}

				@Override
				public void onNothingSelected(AdapterView<?> parentView) {
					Util.toast("Nothing Selected");
				}
			});
			mPatternStringEditPrompt = mMainView.addTextNormal("Custom Java Regex:");
			mPatternStringEditPrompt.setVisibility(View.GONE);
			mPatternStringEdit = mMainView.addEditText(); // Edittext for pattern

			final AlertDialog dlg = (new AlertDialog.Builder(getActivity())) // the final lets us refer to dlg in the handlers..
					.setTitle("Configure")
					.setMessage("Choose how you wish to use this device.")
					.setView(mMainView)
					.setPositiveButton("Apply", null)  // listener to pos button installed below
					.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {}
					}).create();

			dlg.setOnShowListener(new DialogInterface.OnShowListener() {
				@Override
				public void onShow(DialogInterface dialog) {
					Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
					b.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							int which = mChoice.getCheckedRadioButtonId();
							switch (which) {
								case CHOICE_NAVIGATION:
									mDevice.setMode(Mode.NAVIGATION);
									// need to get barcode/attribute
									if (mNavSpinner.getSelectedItemPosition() > 0) {
										NodeAttribute att = (NodeAttribute) mNavSpinner.getSelectedItem();
										mDevice.setNavAttribute(att);
									}
									break;
								case CHOICE_SCORING:
									mDevice.setMode(Mode.SCORING);
									// get trait
									if (mTraitSpinner.getSelectedItemPosition() > 0) {
										Trait trt = (Trait) mTraitSpinner.getSelectedItem();
										mDevice.setTrait(trt);
									}
									break;
								default:
									Util.msg("Please indicate whether to use for navigating or scoring");
									return;
							}

							// Pattern string:
							DeviceType dt = (DeviceType) mDevTypeSpinner.getSelectedItem();
							mDevice.mDeviceType = dt;
							switch (dt) {
								case CUSTOM: // Get user entered pattern if present
									mPatternString = mPatternStringEdit.getText().toString();
									if (mPatternString != null && mPatternString.length() > 2)
										try {
											mDevice.mPattern = Pattern.compile(mPatternString);
										}
										catch (Exception ex) {
											Util.msg("Invalid pattern");
											return;  // test this
											//mDevice.mPattern = null;
										}
									break;
								case MOTOROLA_CS300: // ends with /r
								case ALLFLEX:  // eg "LA 982 123545\r\n"
								case METTLER_TOLEDO_ML3002: // {27,101,110,116,101,114,46};   // bytes at end of Mettler Toledo scale send
								case METTLER_TOLEDO_ML4001:
								case DECIMAL_IN_STRING:
									mDevice.mPattern = dt.getPattern();
									break;
								case ZEBRA_PRINTER:
								case AUTO_DETECT:
								default:
									mDevice.mPattern = null;
									break;
							}

							((ActBluetooth) getActivity()).updateButtonsAndDetails();
							dlg.dismiss();
						}
					});
				}
			});
			return dlg;
		}
	}
}

