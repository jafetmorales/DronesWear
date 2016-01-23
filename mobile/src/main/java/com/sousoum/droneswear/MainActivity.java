package com.sousoum.droneswear;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.sousoum.discovery.Discoverer;
import com.sousoum.drone.ParrotDrone;
import com.sousoum.drone.ParrotDroneFactory;
import com.sousoum.drone.ParrotFlyingDrone;
import com.sousoum.shared.AccelerometerData;
import com.sousoum.shared.ActionType;
import com.sousoum.shared.Message;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, NodeApi.NodeListener, Discoverer.DiscovererListener, ParrotDrone.ParrotDroneListener
{
    private static final String TAG = "MobileMainActivity";

    private static final int ALPHA_ANIM_DURATION = 500;

    private GoogleApiClient mGoogleApiClient;

    private final Object mDroneLock = new Object();
    private final Object mNodeLock = new Object();

    private ParrotDrone mDrone;

    private ArrayList<Node> mNodes;

    private View mTimeoutHelper;
    private Button mEmergencyBt;
    private TextView mTextView;
    private Switch mAcceleroSwitch;

    private Handler mHandler;
    private Runnable mAnimRunnable;

    static {
        ARSDK.loadSDKLibs();
    }

    private boolean mUseWatchAccelero;
    private Discoverer mDiscoverer;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTimeoutHelper = findViewById(R.id.timeout_helper);
        mTextView = (TextView)findViewById(R.id.textView);
        mAcceleroSwitch = (Switch)findViewById(R.id.acceleroSwitch);
        mAcceleroSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b)
            {
                onAcceleroSwitchCheckChanged();
            }
        });
        mEmergencyBt = (Button)findViewById(R.id.emergencyBt);
        mEmergencyBt.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                onEmergencyClicked();
            }
        });

        mUseWatchAccelero = true;
        mAcceleroSwitch.setChecked(true);

        mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addApi(Wearable.API).build();

        mNodes = new ArrayList<>();

        mDiscoverer = new Discoverer(this);
        mDiscoverer.addListener(this);

        mHandler = new Handler(getMainLooper());
        mAnimRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                startBounceAnimation();
            }
        };
    }

    @Override
    protected void onPause(){
        super.onPause();

        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()){
            mGoogleApiClient.disconnect();
        }

        mDiscoverer.cleanup();
    }

    @Override
    protected void onResume(){
        super.onResume();

        mGoogleApiClient.connect();

        mDiscoverer.setup();
    }


    private void sendActionType(int actionType)
    {
        synchronized (mNodeLock) {
            if (!mNodes.isEmpty()) {
                Message.sendActionTypeMessage(actionType, mGoogleApiClient);
            }
        }
    }

    //region ParrotDroneListener
    @Override
    public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state)
    {
        switch (state) {
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                mDiscoverer.stopDiscovering();
                mTextView.setText(R.string.device_connected);
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                synchronized (mDroneLock)
                {
                    mDrone = null;
                }
                mTextView.setText(R.string.device_disconnected);
                mDiscoverer.startDiscovering();
                break;
        }
    }

    @Override
    public void onDroneActionChanged(int action)
    {
        switch (action) {
            case ActionType.ACTION_TYPE_LAND:
                mEmergencyBt.setVisibility(View.VISIBLE);
                break;
            default:
                mEmergencyBt.setVisibility(View.GONE);
                break;
        }

        sendActionType(action);
    }
    //endregion ParrotDroneListener

    //region DiscovererListener
    @Override
    public void onServiceDiscovered(ARDiscoveryDeviceService deviceService)
    {
        mTimeoutHelper.setVisibility(View.GONE);
        mTextView.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mAnimRunnable);
        if (mDrone == null)
        {
            mTextView.setText(String.format(getString(R.string.connecting_to_device), deviceService.getName()));

            synchronized (mDroneLock)
            {
                mDrone = ParrotDroneFactory.createParrotDrone(deviceService, this);
                mDrone.addListener(this);
            }
        }
    }

    @Override
    public void onDiscoveryTimedOut()
    {
        mTimeoutHelper.setVisibility(View.VISIBLE);
        mTextView.setVisibility(View.GONE);
        startAnimation();
    }
    //endregion DiscovererListener

    private void startAnimation() {
        ArrayList<View> layoutArray = new ArrayList<>();
        layoutArray.add(findViewById(R.id.timeout_helper_1));
        layoutArray.add(findViewById(R.id.timeout_helper_2));
        layoutArray.add(findViewById(R.id.timeout_helper_3));
        layoutArray.add(findViewById(R.id.timeout_helper_4));
        layoutArray.add(findViewById(R.id.timeout_helper_5));

        ObjectAnimator animation;

        View timeoutTitle = findViewById(R.id.timeout_title);
        animation = ObjectAnimator.ofFloat(timeoutTitle, View.ALPHA, 0, 1);
        animation.setDuration(ALPHA_ANIM_DURATION);
        animation.start();

        for (View view : layoutArray) {
            animation = ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1);
            animation.setDuration(ALPHA_ANIM_DURATION);
            animation.start();
        }

        startBounceAnimation();
    }

    private void startBounceAnimation() {
        ArrayList<View> layoutArray = new ArrayList<>();
        layoutArray.add(findViewById(R.id.timeout_helper_1));
        layoutArray.add(findViewById(R.id.timeout_helper_2));
        layoutArray.add(findViewById(R.id.timeout_helper_3));
        layoutArray.add(findViewById(R.id.timeout_helper_4));
        layoutArray.add(findViewById(R.id.timeout_helper_5));

        ObjectAnimator animation;
        int translationX = 30;
        int animationDuration = 200;
        int animationDelay = 50;
        int layoutIdx = 0;

        for (View view : layoutArray) {
            animation = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, translationX);
            animation.setDuration(animationDuration);
            animation.setStartDelay((animationDelay * layoutIdx) + ALPHA_ANIM_DURATION);
            animation.start();

            layoutIdx++;
        }

        layoutIdx = 0;
        for (View view : layoutArray) {
            animation = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0);
            animation.setDuration(animationDuration);
            animation.setStartDelay((animationDelay * layoutIdx) + ALPHA_ANIM_DURATION + animationDuration);
            animation.start();

            layoutIdx++;
        }

        mHandler.postDelayed(mAnimRunnable, 5000);
    }

    //region Button Listeners
    private void onEmergencyClicked() {
        if (mDrone != null && (mDrone instanceof ParrotFlyingDrone))
        {
            ((ParrotFlyingDrone) mDrone).sendEmergency();
        }
    }

    private void onAcceleroSwitchCheckChanged() {
        mUseWatchAccelero = mAcceleroSwitch.isChecked();
        Log.i(TAG, "Accelero is checked = " + mUseWatchAccelero);
        if (!mUseWatchAccelero && mDrone != null)
        {
            mDrone.stopPiloting();
        }
    }

    //endregion Button Listeners

    //region DataApi.DataListener
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {

            DataItem dataItem = event.getDataItem();
            Message.MESSAGE_TYPE messageType = Message.getMessageType(dataItem);

            if (event.getType() == DataEvent.TYPE_CHANGED) {
                switch (messageType) {
                    case ACC:
                        synchronized (mDroneLock) {
                            if (mDrone != null && mUseWatchAccelero)
                            {
                                AccelerometerData accelerometerData = Message.decodeAcceleroMessage(dataItem);
                                if (accelerometerData != null)
                                {
                                    mDrone.pilotWithAcceleroData(accelerometerData);
                                } else {
                                    mDrone.stopPiloting();
                                }
                            }

                        }
                        break;
                    case ACTION:
                        if (mDrone != null)
                        {
                            mDrone.sendAction();
                        }
                        break;
                }

            }
        }
    }
    //endregion DataApi.DataListener

    //region GoogleApiClient.ConnectionCallbacks
    @Override
    public void onConnected(Bundle bundle)
    {
        Wearable.NodeApi.addListener(mGoogleApiClient, this);
        //Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.DataApi.addListener(mGoogleApiClient, this);

        PendingResult<NodeApi.GetConnectedNodesResult> results = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        results.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>()
        {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult)
            {
                if (getConnectedNodesResult.getStatus().isSuccess())
                {
                    synchronized (mNodeLock)
                    {
                        mNodes.addAll(getConnectedNodesResult.getNodes());
                    }
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.i(TAG, "onConnectionSuspended");
    }
    //endregion GoogleApiClient.ConnectionCallbacks

    //region DataAPI
    @Override
    public void onPeerConnected(Node node)
    {
        synchronized (mNodeLock)
        {
            Log.i(TAG, "Adding node = " + node);
            mNodes.add(node);
        }
    }

    @Override
    public void onPeerDisconnected(Node node)
    {
        synchronized (mNodeLock)
        {
            Log.i(TAG, "removing node = " + node);
            mNodes.remove(node);
        }
    }
    //endregion DataAPI
}
