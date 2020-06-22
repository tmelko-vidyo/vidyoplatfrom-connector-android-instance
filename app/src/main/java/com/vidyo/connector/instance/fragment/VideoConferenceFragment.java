package com.vidyo.connector.instance.fragment;

import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.connector.instance.R;
import com.vidyo.connector.instance.event.ControlEvent;
import com.vidyo.connector.instance.instance.ConnectorApi;
import com.vidyo.connector.instance.instance.ConnectorInstance;
import com.vidyo.connector.instance.utils.AppUtils;
import com.vidyo.connector.instance.utils.ConnectParams;
import com.vidyo.connector.instance.utils.Logger;
import com.vidyo.connector.instance.view.ControlToolbarView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class VideoConferenceFragment extends Fragment implements Connector.IConnect, View.OnLayoutChangeListener {

    private ControlToolbarView controlView;
    private View progressBar;

    private FrameLayout videoFrame;
    private ConnectorApi connectorApi;

    private boolean quitState = false;

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);

        if (connectorApi != null) {
            ControlToolbarView.State state = controlView.getState();
            connectorApi.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground);

            connectorApi.setCameraPrivacy(state.isMuteCamera());
            connectorApi.setMicrophonePrivacy(state.isMuteMic());
            connectorApi.setSpeakerPrivacy(state.isMuteSpeaker());
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);

        if (connectorApi != null) {
            connectorApi.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Background);

            connectorApi.setCameraPrivacy(true);
            connectorApi.setMicrophonePrivacy(true);
            connectorApi.setSpeakerPrivacy(true);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.conference_layout, container, false);

        progressBar = view.findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);

        controlView = view.findViewById(R.id.control_view);

        videoFrame = view.findViewById(R.id.video_frame);

        connectorApi = new ConnectorInstance(getActivity(), videoFrame, this);
        Logger.i("Connector instance has been created.");

        controlView.showVersion(connectorApi.getVersion(), false);

        /* Register view layout update */
        videoFrame.addOnLayoutChangeListener(this);
        return view;
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        view.removeOnLayoutChangeListener(this);

        int width = view.getWidth();
        int height = view.getHeight();
        Logger.i("Show View at Called: " + width + ", " + height);

        connectorApi.showViewAt(view, width, height);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isAdded() || getActivity() == null) return;

        Logger.i("Config change requested.");

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        FrameLayout.LayoutParams videoViewParams = new FrameLayout.LayoutParams(width, height);
        videoFrame.setLayoutParams(videoViewParams);

        videoFrame.addOnLayoutChangeListener(this);
        videoFrame.requestLayout();
    }


    @Override
    public void onSuccess() {
        if (!isAdded() || getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), R.string.connected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(true);
            controlView.disable(false);
        });
    }

    @Override
    public void onFailure(final Connector.ConnectorFailReason connectorFailReason) {
        if (!isAdded() || getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), connectorFailReason.name(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.disable(false);

            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @Override
    public void onDisconnected(Connector.ConnectorDisconnectReason connectorDisconnectReason) {
        if (!isAdded() || getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), R.string.disconnected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.disable(false);

            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if (quitState) {
                getActivity().finish();
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void controlEvents(ControlEvent event) {
        if (connectorApi == null) return;

        switch (event.getCall()) {
            case CONNECT_DISCONNECT:
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                progressBar.setVisibility(View.VISIBLE);
                controlView.disable(true);

                toggleConnectOrDisconnect((boolean) event.getValue());
                break;
            case MUTE_CAMERA:
                connectorApi.setCameraPrivacy((boolean) event.getValue());
                break;
            case MUTE_MIC:
                connectorApi.setMicrophonePrivacy((boolean) event.getValue());
                break;
            case MUTE_SPEAKER:
                connectorApi.setSpeakerPrivacy((boolean) event.getValue());
                break;
            case CYCLE_CAMERA:
                connectorApi.cycleCamera();
                break;
            case DEBUG_OPTION:
                boolean value = (boolean) event.getValue();
                if (value) {
                    connectorApi.enableDebug(7776, "");
                } else {
                    connectorApi.disableDebug();
                }

                Toast.makeText(requireContext(), getString(R.string.debug_option) + value, Toast.LENGTH_SHORT).show();
                break;
            case SEND_LOGS:
                AppUtils.sendLogs(getActivity());
                break;

            case BACK_PRESSED:
                if (!connectorApi.isConnected()) {
                    getActivity().finish();
                } else {
                    if (quitState) return;

                    quitState = true;
                    toggleConnectOrDisconnect(false);
                }
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        connectorApi.release();
        Logger.i("Connector instance has been released.");
    }

    private void toggleConnectOrDisconnect(boolean connectOrDisconnect) {
        progressBar.setVisibility(View.VISIBLE);
        controlView.disable(true);

        if (connectOrDisconnect) {
            connectorApi.connect(ConnectParams.PORTAL_HOST, ConnectParams.PORTAL_ROOM, ConnectParams.DISPLAY_NAME, ConnectParams.PORTAL_PIN, this);
        } else {
            if (connectorApi != null) connectorApi.disconnect();
        }
    }
}