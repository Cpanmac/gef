/*******************************************************************************
 * Copyright (c) 2014 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthias Wienand (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef4.mvc.fx.policies;

import java.util.List;

import javafx.geometry.Point2D;
import javafx.scene.Node;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.gef4.fx.anchors.FXStaticAnchor;
import org.eclipse.gef4.fx.anchors.IFXAnchor;
import org.eclipse.gef4.fx.nodes.IFXConnection;
import org.eclipse.gef4.geometry.planar.Point;
import org.eclipse.gef4.mvc.fx.operations.FXReconnectEndPointOperation;
import org.eclipse.gef4.mvc.fx.operations.FXReconnectEndPointOperation.AnchorKind;
import org.eclipse.gef4.mvc.fx.parts.AbstractFXContentPart;
import org.eclipse.gef4.mvc.operations.ITransactional;
import org.eclipse.gef4.mvc.parts.IContentPart;
import org.eclipse.gef4.mvc.policies.AbstractPolicy;

//TODO: find a better name
public class FXReconnectPolicy extends AbstractPolicy<Node> implements
ITransactional {

	private boolean isStartAnchor;
	private Point2D startPointScene;
	private Point2D startPointLocal;
	private IFXConnection connection;
	private IFXAnchor initialAnchor;
	private IFXAnchor currentAnchor;
	private FXReconnectEndPointOperation op;

	@Override
	public IUndoableOperation commit() {
		getHost().setRefreshVisual(true);
		return op;
	}

	public void dragTo(Point pointInScene,
			List<IContentPart<Node>> partsUnderMouse) {
		Point position = transformToLocal(pointInScene);
		AbstractFXContentPart anchorPart = getAnchorPart(partsUnderMouse);

		if (anchorPart != null) {
			currentAnchor = anchorPart.getAnchor(getHost());
		} else {
			currentAnchor = new FXStaticAnchor(getHost().getVisual(), position);
		}
		op = new FXReconnectEndPointOperation("Reconnect", connection, initialAnchor,
				currentAnchor, isStartAnchor ? AnchorKind.START
						: AnchorKind.END);

		// execute locally
		try {
			op.execute(null, null);
		} catch (ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	protected AbstractFXContentPart getAnchorPart(
			List<IContentPart<Node>> partsUnderMouse) {
		for (IContentPart<Node> cp : partsUnderMouse) {
			AbstractFXContentPart part = (AbstractFXContentPart) cp;
			IFXAnchor anchor = part.getAnchor(getHost());
			if (anchor != null) {
				return part;
			}
		}
		return null;
	}

	private IFXConnection getConnection() {
		return (IFXConnection) getHost().getVisual();
	}

	@Override
	public void init() {
		getHost().setRefreshVisual(false);
	}

	public void press(boolean isStart, Point startPointInScene) {
		isStartAnchor = isStart;
		startPointScene = new Point2D(startPointInScene.x, startPointInScene.y);
		startPointLocal = getHost().getVisual().sceneToLocal(startPointScene);
		connection = getConnection();
		if (isStartAnchor) {
			initialAnchor = connection.getStartAnchor();
		} else {
			initialAnchor = connection.getEndAnchor();
		}
		currentAnchor = initialAnchor;
		op = new FXReconnectEndPointOperation("Reconnect", connection, initialAnchor,
				currentAnchor, isStartAnchor ? AnchorKind.START
						: AnchorKind.END);
	}

	protected Point transformToLocal(Point p) {
		Point2D pLocal = getHost().getVisual().sceneToLocal(p.x, p.y);
		Point2D initialPosLocal = getHost().getVisual().sceneToLocal(
				startPointScene);

		Point delta = new Point(pLocal.getX() - initialPosLocal.getX(),
				pLocal.getY() - initialPosLocal.getY());

		return new Point(startPointLocal.getX() + delta.x,
				startPointLocal.getY() + delta.y);
	}

}