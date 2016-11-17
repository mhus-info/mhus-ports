package org.vaadin.miki.flatselect.client;

import com.vaadin.shared.ui.select.AbstractSelectState;

public class FlatSelectState extends AbstractSelectState {

    public int value = -1;
    public int optionsPerRow = 0;
    public String[] options = new String[0];

}