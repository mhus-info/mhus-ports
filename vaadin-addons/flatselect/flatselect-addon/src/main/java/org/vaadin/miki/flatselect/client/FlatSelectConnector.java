package org.vaadin.miki.flatselect.client;

import org.vaadin.miki.flatselect.FlatSelect;

import com.vaadin.client.communication.RpcProxy;
import com.vaadin.client.communication.StateChangeEvent;
import com.vaadin.client.ui.AbstractComponentConnector;
import com.vaadin.shared.ui.Connect;

// Connector binds client-side widget class to server-side component class
// Connector lives in the client and the @Connect annotation specifies the
// corresponding server-side component
@Connect(FlatSelect.class)
public class FlatSelectConnector extends AbstractComponentConnector {

    // ServerRpc is used to send events to server. Communication implementation
    // is automatically created here
    FlatSelectServerRpc rpc = RpcProxy.create(FlatSelectServerRpc.class, this);

    private final FlatSelectWidget.Callback callback = new FlatSelectWidget.Callback() {
        @Override
        public void buttonClicked(int index) {
            rpc.selected(index);
        }
    };

    // We must implement getWidget() to cast to correct type
    // (this will automatically create the correct widget type)
    @Override
    public FlatSelectWidget getWidget() {
        return (FlatSelectWidget) super.getWidget();
    }

    // We must implement getState() to cast to correct type
    @Override
    public FlatSelectState getState() {
        return (FlatSelectState) super.getState();
    }

    // Whenever the state changes in the server-side, this method is called
    @Override
    public void onStateChanged(StateChangeEvent stateChangeEvent) {
        super.onStateChanged(stateChangeEvent);
        getWidget().setOptions(callback, getState().options, getState().optionsPerRow, getState().value);
    }
}
