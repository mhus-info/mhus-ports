package org.vaadin.miki.flatselect.client;

import com.vaadin.shared.MouseEventDetails;
import com.vaadin.shared.communication.ServerRpc;

// ServerRpc is used to pass events from client to server
public interface FlatSelectServerRpc extends ServerRpc {

    void selected(int itemIndex);

}
