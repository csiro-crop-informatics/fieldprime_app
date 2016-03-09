/*
 * ActBluetooth.java
 * Michael Kirk 2013
 *
 * Bluetooth activity.
 * Allows user to search for and connect to bluetooth device.
 * UI allows the to specify for each connection whether it is used for:
 * Navigation
 * Scoring for a specific trait
 * Scoring for any trait.
 *
 * ToDo
 * Save MyBluetoothDevices against hardware device ids, remake open connections
 * after trial/app close and reopen.
 * Add disconnect option - should be able to keep configuration when disconnected?
 * Yes I think so. Either when device disconnects or user initiated.
 * Maybe timer to keep trying to connect?
 */

package csiro.fieldprime;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

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
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import csiro.fieldprime.Trial.NodeAttribute;
//import csiro.fieldprime.Trial.NodeProperty;

/*
 * Show list of paired devices, have search button to optionally also show
 * unpaired devices. On selection of device (paired or not), create dialog
 * for user to specify whether the device is to be used for navigation or
 * score entry, and if for score entry, which - if any - traits it should
 * be restricted to.
 * NB, we don't seem to need to explicitly pair to a device to connect to it.
 */

public class ActBluetooth extends VerticalList.VLActivity {
    interface handlers {
		//void handleNavigationValue(String barcode, NodeAttribute barcodeAttribute);
		//void handleBluetoothDataValue(String value, Trait trt);
		void handleBluetoothValue(String value, MyBluetoothDevice btDev);
    }

	final int SCAN = 1;
	final int CONNECT = 2;
	final int DISCONNECT = 3;

	private BluetoothAdapter mBluetoothAdapter = null;
	private ArrayList<MyBluetoothDevice> mDeviceList = new ArrayList<MyBluetoothDevice>();
	private TextView mText;
	private ButtonBar mButtonBar;
	private Button mConnectButton;

    static final byte  mMTEnd[] = new byte[] {27,101,110,116,101,114,46};   // bytes at end of Mettler Toledo scale send

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
		//mDeviceAdapter.clear();
		//mDeviceList = new ArrayList<MyBluetoothDevice>();
		// Clear all devices that aren't connected:

//		Iterator<MyBluetoothDevice> iter = mDeviceList.iterator();
//		while (iter.hasNext()) {
//		    if (!iter.next().connected()) {
//		        iter.remove();
//		    }
//		}

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

	@Override
	String heading() {
		return "Bluetooth Devices";
	}

	@Override
	void listSelect(int index) {
		MyBluetoothDevice md = mDeviceList.get(index);
		// if connected, add disconnect button
		String label = md.connected() ? "Disconnect" : "Connect";
		if (mConnectButton == null)
			mConnectButton = mButtonBar.addButton(label, CONNECT);
		else
			mConnectButton.setText(label);
		mText.setText(md.details());
	}
		
	@Override
	View getMidView() {
		mText = super.makeTextView();
		return mText; 
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

	private boolean isPrinter(BluetoothDevice btd) {
		return (btd.getBluetoothClass().getDeviceClass() == 1664);
	}

//	private void connect(final MyBluetoothDevice device) {
//		if (isPrinter(device.device())) {
//			device.device().getAddress();
//			// Get and remember the mac address, have to get this back to the scoring activity
//			g.setPrinterMacAddr(device.device().getAddress());
//			Util.msg("Set as current printer");
//			return;
//		}
//
//		DlgBTconnect.newInstance(device);
//		return;
//    }

//	private void disconnect(final MyBluetoothDevice device) {
//		device.myCancel();
//		if (isPrinter(device.device())) {
//			device.device().getAddress();
//			// Get and remember the mac address, have to get this back to the scoring activity
//			g.setPrinterMacAddr(device.device().getAddress());
//			Util.msg("Set as current printer");
//			return;
//		}
//
//		DlgBTconnect.newInstance(device);
//		return;
//    }

	@Override
	View getBottomView() {
mConnectButton = null;
		mButtonBar = new ButtonBar(this, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (v.getId()) {
				case SCAN:
					scan();   // MFK how about disabling stuff while scanning?
					break;
				case CONNECT:
// may be disconnect, check label, and/or isConnected() (which should be kept accurate in all cases)
					if (mCurrSelection < 0) break;
					mDeviceList.get(mCurrSelection).connect();
					break;
				case DISCONNECT:
					if (mCurrSelection < 0) break;
					mDeviceList.get(mCurrSelection).disconnect();
					break;
				}
			}
		});
		mButtonBar.addButton("scan", SCAN);
// Connect button now added and managed in listSelect.
//		mConnectButton = mButtonBar.addButton("Connect", CONNECT);
		return mButtonBar.Layout();
	}
	
	@Override
	void hideKeyboard() {}

	private handlers getHandler() {
		Activity curr = g.getCurrentActivity();
		if (curr instanceof handlers)
			return (handlers)curr;
		else
			return null;
	}

	/*** class MyBluetoothDevice: ************************************************************************************
     *
     * Bluetooth device with some extra stuff. (Note extending BluetoothDevice is not possible, it seems).
     * These are used in an ArrayAdapter, and we use toString to define presentation string in the list.
     * Each device may have an associated trait (mTrait), if this is set, then the scores from the
     * device should only be used for the specified trait.
     * If isNavigator() then values from this device should be interpreted as plot barcodes.
     */
    class MyBluetoothDevice{
      	private BluetoothDevice mDevice;
      	private boolean mNavigator;
		private Trial.NodeAttribute mBarcodeAttribute;
      	private Trait mTrait;
      	private boolean mConnected;
		private BluetoothConnection mBtConnection;
      	
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
    		String config;
    		if (!mConnected) {
    			config = "Not connected";
    		} else {
    			config = "Connected";
    			if (isNavigator()) config += " for navigation";
    			else if (mTrait == null) config += " for scoring any trait";
    			else config += " for scoring trait " + mTrait.getCaption();
    		}
    		return mDevice.getName() + "\n" + mDevice.getAddress() + " " + bondState + " " + config;
    	}
    	public BluetoothDevice device() { return mDevice; }
		public String getName() { return mDevice.getName();}
    	public Trait getTrait() { return mTrait; }
    	public void setTrait(Trait t) {
			mTrait = t;
			mBarcodeAttribute = null;
		}
    	public void setNavigator(boolean nav) { mNavigator = nav; }
		public void setNavAttribute(NodeAttribute att) {
			mBarcodeAttribute = att;
			mTrait = null;
		}
    	public boolean isNavigator() { return mNavigator; }
    	public void setConnected(boolean connected) { mConnected = connected; }
    	public boolean connected() { return mConnected; }

		public CharSequence details() {
			return toString();
		}

		public NodeAttribute getBarcodeAttribute() { return mBarcodeAttribute; }

		private void connect() {
			if (isPrinter(device())) {
				device().getAddress();
				// Get and remember the mac address, have to get this back to the scoring activity
				g.setPrinterMacAddr(device().getAddress());
				Util.msg("Set as current printer");
				return;
			}
			DlgBTconnect.newInstance(this);
			return;
		}

		void disconnect() {
			if (mBtConnection != null)
				mBtConnection.myCancel();
			setConnected(false);
		}

		public void asyncRunDevice() {
			Util.toast("Trying to connect to " + mDevice.getName());
			if (connected()) {
				Util.toast("Already connected to " + mDevice.getName());
			} else {
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
			}
		}
	}

	/*** class BluetoothConnection: ***********************************************************************************
	 *
	 * AsyncTask for handling communication with single bluetooth device.
     * NB, to stop the thread call myCancel().
     */
    class BluetoothConnection extends AsyncTask<MyBluetoothDevice, String, Void> {
    	MyBluetoothDevice mDevice;
    	private Exception exception;
        private final String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";   // standard uuid
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
    			byte[] buffer = new byte[128];
    			do {
    				try {
    					red = mStream.read(buffer, curLen, 128 - curLen);
    					if (red == -1) {
    						Util.toast("End of stream reached");  // MFK remove, also can we detect if connection lost?
    					}
    					curLen += red;
    					
    					// Motorola barcode scanner case:
    					if (buffer[curLen - 1] == '\r') {
    						String data = new String(buffer, 0, curLen - 1);
    						curLen = 0;
    						publishProgress(data);
    					}
    					
    					// Mettler-Toledo scale case:
    					else if (curLen > 7) {
    						int i;
    						for (i = 0; i<7; ++i) {
    							if (buffer[i + curLen - 7] != mMTEnd[i])
    								break;
    						}
    						if (i == 7) {
        						String data = new String(buffer, 0, curLen - 7);
        						curLen = 0;
        						publishProgress(data);  							
    						}   						
    					}
    					
    				} catch (Exception ex) {
    					red = -1;
    				}
    			} while (red > 0 && !isCancelled());
    		} catch (IOException e) {
    			// I think we get here if no connection established, but we handle that in onProgressUpdate.
    			this.exception = e;
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
    	}
    	
    	@Override
    	protected void onProgressUpdate(String... values) {
    		if (values[0].equals("xxx")) {
    			mDevice.setConnected(true);
    			Util.toast("Connection Established");
    			fillScreen();   // We need to change the text of just connected device
    		} else {
    			handlers h = getHandler();
    			if (h != null) {
					h.handleBluetoothValue(values[0], mDevice);
    			}
    		}
    		super.onProgressUpdate(values);
    	}
    	
    	@Override
    	protected void onPostExecute(Void v) {
    		if (exception != null) {
    			Util.msg("Cannot establish connection to " + mDevice.getName());
    			Util.exceptionHandler(exception, "BluetoothConnection:onPostExecute");
    		} else {
    			Util.msg("Bluetooth listener stopped for device " + mDevice.getName());
    		}
    		mDevice.setConnected(false);
    	}
    }

	/*** class DlgBTconnect: ***********************************************************************************
	 *
	 * Dialog fragment to determine how user wishes to use the device, and then initiate the connection.
	 */
	public static class DlgBTconnect extends DialogFragment {
//MFK rather than making this robust thru restart, perhaps we can just detect and close?
		private static DlgBTconnect instance = null;    // only allow one Search instance at a time
		private  VerticalList mMainView;
		private  RadioGroup mChoice;
		private static final int CHOICE_NAVIGATION = 1;
		private static final int CHOICE_ANY_TRAIT = 2;
		private static final int CHOICE_SPECIFIC_TRAIT = 3;
		private TextView mNav;
//		private TextView mAnyTrait;
		private TextView mSpecificTrait;
		private Spinner mSpecificTraitSpinner, mNavSpinner;

		private static MyBluetoothDevice mDevice;

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
					new String[]{"Use for navigation", "Use to score any trait", "Use to score a specific trait"},
					new int[]{CHOICE_NAVIGATION, CHOICE_ANY_TRAIT, CHOICE_SPECIFIC_TRAIT},
					new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							RadioButton rb = (RadioButton) v;
							int which = v.getId();
							// Clear/show choice specific items:
							mNav.setVisibility(which == CHOICE_NAVIGATION ? View.VISIBLE : View.GONE);
							mNavSpinner.setVisibility(which == CHOICE_NAVIGATION ? View.VISIBLE : View.GONE);
							mSpecificTrait.setVisibility(which == CHOICE_SPECIFIC_TRAIT ? View.VISIBLE : View.GONE);
							mSpecificTraitSpinner.setVisibility(which == CHOICE_SPECIFIC_TRAIT ? View.VISIBLE : View.GONE);
							// reset both spinners to avoid remembered state:
							mNavSpinner.setSelection(0);
							mSpecificTraitSpinner.setSelection(0);
						}
					});

			// MFK, here and in other places (DlgFilter) we have a prompt and a spinner.
			// Perhaps make a VerticalList object for this. With visibility set option
			/*
			 * Navigation extras:
			 * Note in the attribute list we put at position 0 (default) an option that
			 * is not an attribute but indicates the barcode field of the Node class.
			 * We may eventually remove that field, in which case if the user doesn't
			 * specify an attribute we just search for trait barcode attributes.
			 * MFK note currently CHOICE_ANY_TRAIT is the same as CHOICE_SPECIFIC_TRAIT
			 * with not trait selected, may as well remove CHOICE_ANY_TRAIT.
			 */
			mNav = mMainView.addTextNormal("Navigation attribute:");
			mNav.setVisibility(View.GONE);
			mNavSpinner = mMainView.addSpinner(trl.getAttributes(), " Barcode (default)", null);
			mNavSpinner.setPrompt("coconuts");
			mNavSpinner.setVisibility(View.GONE);
			// Specific trait extras:
			mSpecificTrait = mMainView.addTextNormal("Trait to score:");
			mSpecificTrait.setVisibility(View.GONE);
			mSpecificTraitSpinner = mMainView.addSpinner(new ArrayList<Trait>(Arrays.asList(trl.getTraitList())),
					"..Select Trait..", null);
			mSpecificTraitSpinner.setVisibility(View.GONE);

			final AlertDialog dlg = (new AlertDialog.Builder(getActivity())) // the final lets us refer to dlg in the handlers..
					.setTitle("Connect")
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
									// need to get barcode/attribute
									// get trait
									if (mNavSpinner.getSelectedItemPosition() > 0) {
										NodeAttribute att = (NodeAttribute) mNavSpinner.getSelectedItem();
										mDevice.setNavAttribute(att);
									}
									break;
								case CHOICE_ANY_TRAIT:
									break;
								case CHOICE_SPECIFIC_TRAIT:
									// get trait
									if (mSpecificTraitSpinner.getSelectedItemPosition() > 0) {
										Trait trt = (Trait) mSpecificTraitSpinner.getSelectedItem();
										mDevice.setTrait(trt);
									}
									break;
							}
							mDevice.setNavigator(which == CHOICE_NAVIGATION);
							mDevice.asyncRunDevice();
							dlg.dismiss();
						}
					});
				}
			});

			return dlg;
		}
	}
}

