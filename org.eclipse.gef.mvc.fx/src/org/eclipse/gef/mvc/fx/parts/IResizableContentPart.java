/*******************************************************************************
 * Copyright (c) 2016 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *     Matthias Wienand (itemis AG) - contributions for Bugzilla #504480
 *
 *******************************************************************************/
package org.eclipse.gef.mvc.fx.parts;

import org.eclipse.gef.fx.utils.NodeUtils;
import org.eclipse.gef.geometry.planar.Dimension;

import javafx.scene.Node;

/**
 * An {@link IContentPart} that supports content related resize.
 *
 * @author anyssen
 * @author mwienand
 *
 * @param <V>
 *            The visual node used by this {@link IResizableContentPart}.
 *
 */
public interface IResizableContentPart<V extends Node> extends IContentPart<V> {

	/**
	 * Returns the current size according to this part's content.
	 *
	 * @return The current size according to this part's content.
	 */
	public Dimension getContentSize();

	/**
	 * Returns the current size according to this
	 * {@link IResizableContentPart}'s visual.
	 *
	 * @return The current size according to this
	 *         {@link IResizableContentPart}'s visual.
	 */
	public default Dimension getVisualSize() {
		return NodeUtils.getShapeBounds(getVisual()).getSize();
	}

	/**
	 * Resizes the content element as specified by the given {@link Dimension}.
	 *
	 * @param totalSize
	 *            The new size.
	 */
	public void setContentSize(Dimension totalSize);

	/**
	 * Resizes the visual of this {@link IResizableContentPart} to the given
	 * size.
	 *
	 * @param totalSize
	 *            The new size for this {@link IResizableContentPart}'s visual.
	 */
	public default void setVisualSize(Dimension totalSize) {
		getVisual().resize(totalSize.width, totalSize.height);
	}

}
