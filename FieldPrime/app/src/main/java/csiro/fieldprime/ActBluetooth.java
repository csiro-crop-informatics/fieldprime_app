package csiro.fieldprime;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
		void handleNavigationValue(String barcode);
		void HandleBluetoothDataValue(String value, Trait trt);
    }

	private BluetoothAdapter mBluetoothAdapter = null;
	private ArrayList<MyBluetoothDevice> mDeviceList = new ArrayList<MyBluetoothDevice>();
	private TextView mText;
	
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
//		    if (!iter.next().Connected()) {
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
		MyBluetoothDevice d = mDeviceList.get(index);
		mText.setText(d.Details());
	}
		
	@Override
	View getMidView() {
		mText = super.makeTextView();
		return mText; 
	}
	
    private void Scan() {
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

	void connect(final MyBluetoothDevice device) {
		if (isPrinter(device.device())) {
			device.device().getAddress();
			// Get and remember the mac address, have to get this back to the scoring activity
			g.setPrinterMacAddr(device.device().getAddress());
			Util.msg("Set as current printer");
			return;
		}
		final Trait[] traits = g.currTrial().GetTraitList();

		/*
		 * Construct list of options for use of device once connected.
		 */
		ArrayList<String> traitNames = new ArrayList<String>();
		traitNames.add("<Use for navigation (plot barcodes)>");
		traitNames.add("<Use for any trait>");
		for (Trait t : traits) {
			traitNames.add(t.getCaption());
		}
		String[] listStrings = new String[traitNames.size()];
		listStrings = traitNames.toArray(listStrings);
		// Show list to user for selection. The selection handler is in the onListSelect function.
		DlgList.newInstanceSingle(0, "Choose Trait to Score", listStrings,  new DlgList.ListSelectHandler() {
			@Override
			public void onListSelect(int index, int which) {
				device.setNavigator(which == 0);
				if (which > 1) {
					device.setTrait(traits[which - 2]);
				}
				Util.toast("Trying to connect to " + device.getName());
				BluetoothConnection btc = new BluetoothConnection();
				ActTrial.mActTrial.addBluetoothConnection(btc);
					/*
					 * MFK This use of mActTrial should be replaced, since in some circumstances
					 * mActTrial will be null.
					 */
				
				// NB we may have multiple bluetooths working so we need them to be able to
				// multitask - hence the call to executeOnExecutor() rather than just execute().
				btc.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new MyBluetoothDevice[] { device });
			}
		});
    }

	@Override
	View getBottomView() {
		final int SCAN = 1;
		final int CONNECT = 2;
		ButtonBar bb = new ButtonBar(this, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (v.getId()) {
				case SCAN:
					Scan();   // MFK how about disabling stuff while scanning?
					break;
				case CONNECT:
					if (mCurrSelection < 0) break;
					connect(mDeviceList.get(mCurrSelection));
					break;
				}
			}
		});
		bb.addButton("Scan", SCAN);
		bb.addButton("Connect", CONNECT);
		return bb.Layout();
	}
	
	@Override
	void hideKeyboard() {}
	
    /*
     * MyBluetoothDevice
     * Bluetooth device with some extra stuff. (Note extending BluetoothDevice is not possible, it seems).
     * These are used in an ArrayAdapter, and we use toString to define presentation string in the list.
     * Each device may have an associated trait (mTrait), if this is set, then the scores from the
     * device should only be used for the specified trait.
     * If isNavigator() then values from this device should be interpreted as plot barcodes.
     */
    class MyBluetoothDevice{
      	private BluetoothDevice mDevice;
      	private boolean mNavigator;
      	private Trait mTrait;
      	private boolean mConnected;
      	
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
    			config = "Not Connected";
    		} else {
    			config = "Connected";
    			if (mNavigator) config += " for navigation";
    			else if (mTrait == null) config += " for scoring any trait";
    			else config += " for scoring trait " + mTrait.getCaption();
    		}
    		return mDevice.getName() + "\n" + mDevice.getAddress() + " " + bondState + " " + config;
    	}
    	public BluetoothDevice device() { return mDevice; }
		public String getName() { return mDevice.getName();}
    	public Trait getTrait() { return mTrait; }
    	public void setTrait(Trait t) { mTrait = t; }
    	public void setNavigator(boolean nav) { mNavigator = nav; }
    	public boolean isNavigator() { return mNavigator; }
    	public void SetConnected(boolean connected) { mConnected = connected; }
    	public boolean Connected() { return mConnected; }

		public CharSequence Details() {
			return toString();
		}
    }
    
	private handlers getHandler() {
		Activity curr = g.getCurrentActivity();
		if (curr instanceof handlers)
			return (handlers)curr;
		else
			return null;
	}

    /*
     * BluetoothConnection
     * Thread to connect to device and redirect the data from it.
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
    			mDevice.SetConnected(true);
    			Util.toast("Connection Established");
    			fillScreen();   // We need to change the text of just connected device
    		} else {
    			handlers h = getHandler();
    			if (h != null) {
	    			if (mDevice.isNavigator())
	    				h.handleNavigationValue(values[0]);
	    			else
	    				h.HandleBluetoothDataValue(values[0], mDevice.getTrait());
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
    		mDevice.SetConnected(false);
    	}
    }
}

