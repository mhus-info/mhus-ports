package org.vaadin.miki.flatselect;

import com.vaadin.data.Container;
import com.vaadin.ui.AbstractSelect;
import org.vaadin.miki.flatselect.client.FlatSelectServerRpc;
import org.vaadin.miki.flatselect.client.FlatSelectState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

// This is the server-side UI component that provides public API 
// for FlatSelect
public class FlatSelect extends AbstractSelect {

    private FlatSelectServerRpc rpc = new FlatSelectServerRpc() {
        @Override
        public void selected(int itemIndex) {
            selectedItemAtIndex(itemIndex);
        }
    };

    public FlatSelect() {
        registerRpc(rpc);
    }

    public FlatSelect(String caption) {
        super(caption);
        registerRpc(rpc);
    }

    public FlatSelect(String caption, Container source) {
        super(caption, source);
        registerRpc(rpc);
    }

    public FlatSelect(String caption, Collection<?> options) {
        super(caption, options);
        registerRpc(rpc);
    }

    @Override
    protected void setInternalValue(Object newValue) {
        super.setInternalValue(newValue);
        getState().value = new ArrayList<>(this.getContainerDataSource().getItemIds()).indexOf(newValue);
    }

    private void selectedItemAtIndex(int index) {
        if(index < 0 || index >= this.getContainerDataSource().size())
            setValue(null);
        else
            setValue(new ArrayList<>(this.getContainerDataSource().getItemIds()).get(index));
    }

    public void setOptionsPerRow(int optionsPerRow) {
        getState().optionsPerRow = optionsPerRow;
    }

    public int getOptionsPerRow() {
        return getState(false).optionsPerRow;
    }
        

    private void updateOptions() {
        getState().options =
                this.getContainerDataSource() != null && this.getContainerDataSource().size() > 0 ?
                        this.getContainerDataSource().getItemIds().stream().map(this::getItemCaption).collect(Collectors.toList()).toArray(new String[0]) :
                        new String[0];
        if(getState(false).value >= getState(false).options.length)
            setValue(null);
        else selectedItemAtIndex(getState(false).value);
    }

    @Override
    public void setContainerDataSource(Container newDataSource) {
        super.setContainerDataSource(newDataSource);
        updateOptions();
    }

    @Override
    public void containerItemSetChange(Container.ItemSetChangeEvent event) {
        super.containerItemSetChange(event);
        updateOptions();
    }

    @Override
    protected FlatSelectState getState(boolean markAsDirty) {
        return (FlatSelectState)super.getState(markAsDirty);
    }

    // We must override getState() to cast the state to FlatSelectState
    @Override
    protected FlatSelectState getState() {
        return (FlatSelectState) super.getState();
    }
}
