package org.vaadin.miki.flatselect.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

// Extend any GWT Widget
public class FlatSelectWidget extends VerticalPanel {

    public static interface Callback {
        public void buttonClicked(int index);
    }

    private static final class OptionButton extends Button {
        private final int index;

        private OptionButton(String caption, int index) {
            this.index = index;
            this.setText(caption);
        }
    }

    public void setOptions(final Callback callback, String[] options, int optionsPerRow, int value) {
        this.clear();
        HorizontalPanel panel = null;
        if(optionsPerRow <= 0) {
            panel = new HorizontalPanel();
            this.add(panel);
        }
        for(int zmp1=0; zmp1<options.length; zmp1++) {
                if ((optionsPerRow > 0) && (zmp1 % optionsPerRow) == 0) {
                    panel = new HorizontalPanel();
                    this.add(panel);
                }


            OptionButton btn = new OptionButton(options[zmp1], zmp1);
            panel.add(btn);
            if(zmp1 == value)
                btn.addStyleName("selected");
            else
                btn.removeStyleName("selected");
            btn.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent clickEvent) {
                    callback.buttonClicked(((OptionButton)clickEvent.getSource()).index);
                }
            });
        }
    }

}