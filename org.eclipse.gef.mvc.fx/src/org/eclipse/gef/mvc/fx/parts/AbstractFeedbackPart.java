/*******************************************************************************
 * Copyright (c) 2014, 2016 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef.mvc.fx.parts;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.gef.fx.listeners.VisualChangeListener;
import org.eclipse.gef.fx.nodes.Connection;
import org.eclipse.gef.geometry.planar.Point;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.transform.Transform;

/**
 * Abstract base implementation for a JavaFX-specific {@link IFeedbackPart}.
 *
 * @author anyssen
 *
 * @param <V>
 *            The visual {@link Node} used by this {@link AbstractFeedbackPart}.
 */
abstract public class AbstractFeedbackPart<V extends Node>
		extends AbstractVisualPart<V> implements IFeedbackPart<V> {

	private final Map<IVisualPart<? extends Node>, Integer> anchorageLinkCount = new HashMap<>();

	// XXX: VisualChangeListener is stateful, so we need to maintain a separate
	// one for each anchorage
	private final Map<IVisualPart<? extends Node>, VisualChangeListener> visualChangeListeners = new HashMap<>();

	private ListChangeListener<Point> geometryListener = new ListChangeListener<Point>() {

		@Override
		public void onChanged(ListChangeListener.Change<? extends Point> c) {
			refreshVisual();
		}
	};

	@Override
	public ReadOnlyObjectProperty<IVisualPart<? extends Node>> adaptableProperty() {
		return parentProperty();
	}

	@Override
	protected void doAddChildVisual(IVisualPart<? extends Node> child,
			int index) {
		throw new UnsupportedOperationException(
				"IFeedbackParts do not support children");
	}

	@Override
	protected void doAttachToAnchorageVisual(
			IVisualPart<? extends Node> anchorage, String role) {
		// we only add one visual change listener per anchorage, so we need to
		// keep track of the number of links to an anchorage (roles)
		int count = anchorageLinkCount.get(anchorage) == null ? 0
				: anchorageLinkCount.get(anchorage);

		if (count == 0) {
			Node anchorageVisual = anchorage.getVisual();
			VisualChangeListener listener = new VisualChangeListener() {
				@Override
				protected void boundsInLocalChanged(Bounds oldBounds,
						Bounds newBounds) {
					refreshVisual();
				}

				@Override
				protected void localToParentTransformChanged(Node observed,
						Transform oldTransform, Transform newTransform) {
					refreshVisual();
				}
			};
			visualChangeListeners.put(anchorage, listener);
			listener.register(anchorage.getVisual(), getVisual());
			// for connections, we need to refresh the handle if the
			// connection's geometry changes, too
			if (anchorageVisual instanceof Connection) {
				Connection connection = (Connection) anchorageVisual;
				connection.pointsUnmodifiableProperty()
						.addListener(geometryListener);
			}
		}
		anchorageLinkCount.put(anchorage, count + 1);
	}

	@Override
	protected void doDetachFromAnchorageVisual(
			IVisualPart<? extends Node> anchorage, String role) {
		// infer current number of links
		int count = anchorageLinkCount.get(anchorage);

		// the anchorage might be registered under a different roles, only
		// remove the listener if there is no link left
		if (count == 1) {
			// now we are sure that we do not need to listen to visual changes
			// of this anchorage any more
			visualChangeListeners.remove(anchorage).unregister();
			Node anchorageVisual = anchorage.getVisual();
			if (anchorageVisual instanceof Connection) {
				((Connection) anchorageVisual).pointsUnmodifiableProperty()
						.removeListener(geometryListener);
			}
		}

		if (count > 1) {
			anchorageLinkCount.put(anchorage, count - 1);
		} else {
			anchorageLinkCount.remove(anchorage);
		}
	}

	@Override
	protected void doRemoveChildVisual(IVisualPart<? extends Node> child,
			int index) {
		throw new UnsupportedOperationException(
				"IFeedbackParts do not support this");
	}

	@Override
	public IVisualPart<? extends Node> getAdaptable() {
		return getParent();
	}

	@Override
	public void setAdaptable(IVisualPart<? extends Node> adaptable) {
		setParent(adaptable);
	}
}
