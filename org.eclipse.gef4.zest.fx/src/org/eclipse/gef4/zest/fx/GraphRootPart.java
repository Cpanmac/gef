package org.eclipse.gef4.zest.fx;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javafx.scene.Node;

import org.eclipse.gef4.geometry.planar.Rectangle;
import org.eclipse.gef4.graph.Graph;
import org.eclipse.gef4.layout.LayoutAlgorithm;
import org.eclipse.gef4.layout.algorithms.SpringLayoutAlgorithm;
import org.eclipse.gef4.layout.interfaces.NodeLayout;
import org.eclipse.gef4.mvc.fx.parts.FXRootPart;
import org.eclipse.gef4.mvc.models.IContentModel;
import org.eclipse.gef4.mvc.models.IViewportModel;
import org.eclipse.gef4.mvc.parts.IContentPart;
import org.eclipse.gef4.zest.layout.GraphLayoutContext;

public class GraphRootPart extends FXRootPart {

	public static final LayoutAlgorithm DEFAULT_LAYOUT_ALGORITHM = new SpringLayoutAlgorithm();

	private LayoutAlgorithm layoutAlgorithm = DEFAULT_LAYOUT_ALGORITHM;

	// FIXME: sugiyama layout exceeds bounds..
	// new SugiyamaLayoutAlgorithm(
	// Direction.VERTICAL, new DFSLayerProvider());

	private PropertyChangeListener contentChanged = new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (IContentModel.CONTENTS_PROPERTY.equals(evt.getPropertyName())) {
				Object content = evt.getNewValue();
				final GraphLayoutContext context = createLayoutContext(content);
				// register flush changes listener
				context.setFlushChanges(new Runnable() {
					@Override
					public void run() {
						adaptLayout(context);
					}
				});

				// set layout algorithm
				context.setStaticLayoutAlgorithm(layoutAlgorithm);
				// context.setIncrementalLayoutAlgorithm(layoutAlgorithm);

				// set layout context. other parts listen for the layout model
				// to send in their layout data
				getViewer().getDomain().getProperty(ILayoutModel.class)
						.setLayoutContext(context);
				applyLayout(context);
			}
		}
	};

	private PropertyChangeListener viewportChanged = new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			String name = evt.getPropertyName();
			if (IViewportModel.VIEWPORT_WIDTH_PROPERTY.equals(name)
					|| IViewportModel.VIEWPORT_HEIGHT_PROPERTY.equals(name)) {
				GraphLayoutContext context = getLayoutContext();
				if (context != null) {
					applyLayout(context);
				}
			}
		}
	};

	@Override
	public void activate() {
		super.activate();
		getViewer().getContentModel().addPropertyChangeListener(contentChanged);
		getViewer().getViewportModel().addPropertyChangeListener(
				viewportChanged);
	}

	protected void adaptLayout(final GraphLayoutContext context) {
		for (NodeLayout nodeLayout : context.getNodes()) {
			Object graphNode = nodeLayout.getItems()[0];
			IContentPart<Node> nodePart = getViewer().getContentPartMap().get(
					graphNode);
			((NodeContentPart) nodePart).adaptLayout();
		}
	}

	protected void applyLayout(final GraphLayoutContext context) {
		// get current viewport size
		IViewportModel viewportModel = getViewer().getViewportModel();
		double width = viewportModel.getWidth();
		double height = viewportModel.getHeight();
		// FIXME: insets of 20px should not be hard coded
		context.setBounds(new Rectangle(0, 0, width - 20, height - 20));

		// apply layout algorithm
		context.applyStaticLayout(true);
		context.flushChanges(false);
	}

	protected GraphLayoutContext createLayoutContext(Object content) {
		if (!(content instanceof List)) {
			throw new IllegalStateException(
					"Wrong content! Expected <List> but got <" + content + ">.");
		}
		if (((List) content).size() != 1) {
			throw new IllegalStateException(
					"Wrong content! Expected <Graph> but got nothing.");
		}
		content = ((List) content).get(0);
		if (!(content instanceof Graph)) {
			throw new IllegalStateException(
					"Wrong content! Expected <Graph> but got <" + content
							+ ">.");
		}
		final GraphLayoutContext context = new GraphLayoutContext(
				(Graph) content);
		return context;
	}

	protected GraphLayoutContext getLayoutContext() {
		ILayoutModel layoutModel = getViewer().getDomain().getProperty(
				ILayoutModel.class);
		if (layoutModel == null) {
			return null;
		}
		return (GraphLayoutContext) layoutModel.getLayoutContext();
	}

	public void setLayoutAlgorithm(LayoutAlgorithm algorithm) {
		layoutAlgorithm = algorithm;
		GraphLayoutContext context = getLayoutContext();
		if (context != null) {
			context.setStaticLayoutAlgorithm(layoutAlgorithm);
			applyLayout(context);
		}
	}

}