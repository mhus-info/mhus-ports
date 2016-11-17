package org.vaadin.jfreechart;

import org.vaadin.addon.JFreeChartWrapper;

public class JFreeChartContainer extends JFreeChartWrapper {

	private static final long serialVersionUID = 1L;

	public JFreeChartContainer(org.jfree.chart.JFreeChart chartToBeWrapped, RenderingMode renderingMode) {
		super(chartToBeWrapped, renderingMode);
	}

	public JFreeChartContainer(org.jfree.chart.JFreeChart chartToBeWrapped) {
		super(chartToBeWrapped);
	}

}
