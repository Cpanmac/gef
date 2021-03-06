/*******************************************************************************
 * Copyright (c) 2014, 2016 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthias Wienand (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef.mvc.fx.policies;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.gef.mvc.fx.operations.ITransactionalOperation;

import javafx.scene.input.ZoomEvent;

/**
 * An {@link IOnPinchSpreadPolicy} that performs zooming.
 *
 * @author anyssen
 *
 */
public class ZoomOnPinchSpreadPolicy extends AbstractInteractionPolicy
		implements IOnPinchSpreadPolicy {

	// gesture validity
	private boolean invalidGesture = false;

	private ChangeViewportPolicy viewportPolicy;

	@Override
	public void abortZoom() {
	}

	/**
	 * Determines the {@link ChangeViewportPolicy} that is used by this
	 * policy.
	 *
	 * @return The {@link ChangeViewportPolicy} that is used by this policy.
	 */
	protected ChangeViewportPolicy determineViewportPolicy() {
		return getHost().getRoot().getAdapter(ChangeViewportPolicy.class);
	}

	@Override
	public void endZoom(ZoomEvent event) {
		if (invalidGesture) {
			return;
		}
		ITransactionalOperation commit = getViewportPolicy().commit();
		if (commit != null && !commit.isNoOp()) {
			try {
				getHost().getRoot().getViewer().getDomain().execute(commit,
						new NullProgressMonitor());
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Returns the {@link ChangeViewportPolicy} that is used by this policy.
	 *
	 * @return The {@link ChangeViewportPolicy} that is used by this policy.
	 */
	protected ChangeViewportPolicy getViewportPolicy() {
		return viewportPolicy;
	}

	/**
	 * Returns whether the given {@link ZoomEvent} should trigger zooming. Per
	 * default, will always return <code>true</code>.
	 *
	 * @param e
	 *            The {@link ZoomEvent} in question.
	 * @return <code>true</code> if the given {@link ZoomEvent} should trigger
	 *         zoom, otherwise <code>false</code>.
	 */
	protected boolean isZoom(ZoomEvent e) {
		return true;
	}

	@Override
	public void startZoom(ZoomEvent e) {
		invalidGesture = !isZoom(e);
		if (invalidGesture) {
			return;
		}
		viewportPolicy = determineViewportPolicy();
		viewportPolicy.init();
	}

	@Override
	public void zoom(ZoomEvent e) {
		if (invalidGesture) {
			return;
		}
		getViewportPolicy().zoom(true, true, e.getZoomFactor(), e.getSceneX(),
				e.getSceneY());
	}

}
