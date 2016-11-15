package org.vaadin.jfreechart;

import org.vaadin.addon.JFreeChartWrapper;

public class JFreeChart extends JFreeChartWrapper {

	private static final long serialVersionUID = 1L;

	public JFreeChart(org.jfree.chart.JFreeChart chartToBeWrapped, RenderingMode renderingMode) {
		super(chartToBeWrapped, renderingMode);
	}

	public JFreeChart(org.jfree.chart.JFreeChart chartToBeWrapped) {
		super(chartToBeWrapped);
	}

}
