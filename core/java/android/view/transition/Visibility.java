/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view.transition;

import android.animation.Animator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.view.ViewParent;

/**
 * This transition tracks changes to the visibility of target views in the
 * start and end scenes. Visibility is determined not just by the
 * {@link View#setVisibility(int)} state of views, but also whether
 * views exist in the current view hierarchy. The class is intended to be a
 * utility for subclasses such as {@link Fade}, which use this visibility
 * information to determine the specific animations to run when visibility
 * changes occur. Subclasses should implement one or both of the methods
 * {@link #appear(ViewGroup, TransitionValues, int, TransitionValues, int), and
 * {@link #disappear(ViewGroup, TransitionValues, int, TransitionValues, int)}.
 *
 * <p>Note that a view's visibility change is determined by both whether the view
 * itself is changing and whether its parent hierarchy's visibility is changing.
 * That is, a view that appears in the end scene will only trigger a call to
 * {@link #appear(android.view.ViewGroup, TransitionValues, int, TransitionValues, int)
 * appear()} if its parent hierarchy was stable between the start and end scenes.
 * This is done to avoid causing a visibility transition on every node in a hierarchy
 * when only the top-most node is the one that should be transitioned in/out.
 * Stability is determined by either the parent hierarchy views being the same
 * between scenes or, if scenes are inflated from layout resource files and thus
 * have result in different view instances, if the views represented by
 * the ids of those parents are stable. This means that visibility determination
 * is more effective with inflated view hierarchies if ids are used.
 * The exception to this is when the visibility subclass transition is
 * targeted at specific views, in which case the visibility of parent views
 * is ignored.</p>
 */
public abstract class Visibility extends Transition {

    private static final String PROPNAME_VISIBILITY = "android:visibility:visibility";
    private static final String PROPNAME_PARENT = "android:visibility:parent";
    private static final String[] sTransitionProperties = {
            PROPNAME_VISIBILITY,
            PROPNAME_PARENT,
    };

    private static class VisibilityInfo {
        boolean visibilityChange;
        boolean fadeIn;
        int startVisibility;
        int endVisibility;
        ViewGroup startParent;
        ViewGroup endParent;
    }

    // Temporary structure, used in calculating state in setup() and play()
    private VisibilityInfo mTmpVisibilityInfo = new VisibilityInfo();

    @Override
    public String[] getTransitionProperties() {
        return sTransitionProperties;
    }

    @Override
    protected void captureValues(TransitionValues values, boolean start) {
        int visibility = values.view.getVisibility();
        values.values.put(PROPNAME_VISIBILITY, visibility);
        values.values.put(PROPNAME_PARENT, values.view.getParent());
    }

    /**
     * Returns whether the view is 'visible' according to the given values
     * object. This is determined by testing the same properties in the values
     * object that are used to determine whether the object is appearing or
     * disappearing in the {@link
     * #play(android.view.ViewGroup, TransitionValues, TransitionValues)}
     * method. This method can be called by, for example, subclasses that want
     * to know whether the object is visible in the same way that Visibility
     * determines it for the actual animation.
     *
     * @param values The TransitionValues object that holds the information by
     * which visibility is determined.
     * @return True if the view reference by <code>values</code> is visible,
     * false otherwise.
     */
    public boolean isVisible(TransitionValues values) {
        if (values == null) {
            return false;
        }
        int visibility = (Integer) values.values.get(PROPNAME_VISIBILITY);
        View parent = (View) values.values.get(PROPNAME_PARENT);

        return visibility == View.VISIBLE && parent != null;
    }

    /**
     * Tests whether the hierarchy, up to the scene root, changes visibility between
     * start and end scenes. This is done to ensure that a view that changes visibility
     * is only animated if that view's parent was stable between scenes; we should not
     * fade an entire hierarchy, but rather just the top-most node in the hierarchy that
     * changed visibility. Note that both the start and end parents are passed in
     * because the instances may differ for the same view due to layout inflation
     * between scenes.
     *
     * @param sceneRoot The root of the scene hierarchy
     * @param startView The container view in the start scene
     * @param endView The container view in the end scene
     * @return true if the parent hierarchy experienced a visibility change, false
     * otherwise
     */
    private boolean isHierarchyVisibilityChanging(ViewGroup sceneRoot, ViewGroup startView,
            ViewGroup endView) {

        if (startView == sceneRoot || endView == sceneRoot) {
            return false;
        }
        TransitionValues startValues = startView != null ?
                getTransitionValues(startView, true) : getTransitionValues(endView, true);
        TransitionValues endValues = endView != null ?
                getTransitionValues(endView, false) : getTransitionValues(startView, false);

        if (startValues == null || endValues == null) {
            return true;
        }
        Integer visibility = (Integer) startValues.values.get(PROPNAME_VISIBILITY);
        int startVisibility = (visibility != null) ? visibility : -1;
        ViewGroup startParent = (ViewGroup) startValues.values.get(PROPNAME_PARENT);
        visibility = (Integer) endValues.values.get(PROPNAME_VISIBILITY);
        int endVisibility = (visibility != null) ? visibility : -1;
        ViewGroup endParent = (ViewGroup) endValues.values.get(PROPNAME_PARENT);
        if (startVisibility != endVisibility || startParent != endParent) {
            return true;
        }

        if (startParent != null || endParent != null) {
            return isHierarchyVisibilityChanging(sceneRoot, startParent, endParent);
        }
        return false;
    }

    private VisibilityInfo getVisibilityChangeInfo(TransitionValues startValues,
            TransitionValues endValues) {
        final VisibilityInfo visInfo = mTmpVisibilityInfo;
        visInfo.visibilityChange = false;
        visInfo.fadeIn = false;
        if (startValues != null) {
            visInfo.startVisibility = (Integer) startValues.values.get(PROPNAME_VISIBILITY);
            visInfo.startParent = (ViewGroup) startValues.values.get(PROPNAME_PARENT);
        } else {
            visInfo.startVisibility = -1;
            visInfo.startParent = null;
        }
        if (endValues != null) {
            visInfo.endVisibility = (Integer) endValues.values.get(PROPNAME_VISIBILITY);
            visInfo.endParent = (ViewGroup) endValues.values.get(PROPNAME_PARENT);
        } else {
            visInfo.endVisibility = -1;
            visInfo.endParent = null;
        }
        if (startValues != null && endValues != null) {
            if (visInfo.startVisibility == visInfo.endVisibility &&
                    visInfo.startParent == visInfo.endParent) {
                return visInfo;
            } else {
                if (visInfo.startVisibility != visInfo.endVisibility) {
                    if (visInfo.startVisibility == View.VISIBLE) {
                        visInfo.fadeIn = false;
                        visInfo.visibilityChange = true;
                    } else if (visInfo.endVisibility == View.VISIBLE) {
                        visInfo.fadeIn = true;
                        visInfo.visibilityChange = true;
                    }
                    // no visibilityChange if going between INVISIBLE and GONE
                } else if (visInfo.startParent != visInfo.endParent) {
                    if (visInfo.endParent == null) {
                        visInfo.fadeIn = false;
                        visInfo.visibilityChange = true;
                    } else if (visInfo.startParent == null) {
                        visInfo.fadeIn = true;
                        visInfo.visibilityChange = true;
                    }
                }
            }
        }
        if (startValues == null) {
            visInfo.fadeIn = true;
            visInfo.visibilityChange = true;
        } else if (endValues == null) {
            visInfo.fadeIn = false;
            visInfo.visibilityChange = true;
        }
        return visInfo;
    }

    @Override
    protected Animator play(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        VisibilityInfo visInfo = getVisibilityChangeInfo(startValues, endValues);
        if (visInfo.visibilityChange) {
            // Only transition views that are either targets of this transition
            // or whose parent hierarchies remain stable between scenes
            boolean isTarget = false;
            if (mTargets != null || mTargetIds != null) {
                View startView = startValues != null ? startValues.view : null;
                View endView = endValues != null ? endValues.view : null;
                int startId = startView != null ? startView.getId() : -1;
                int endId = endView != null ? endView.getId() : -1;
                isTarget = isValidTarget(startView, startId) || isValidTarget(endView, endId);
            }
            if (isTarget || ((visInfo.startParent != null || visInfo.endParent != null) &&
                    !isHierarchyVisibilityChanging(sceneRoot,
                            visInfo.startParent, visInfo.endParent))) {
                if (visInfo.fadeIn) {
                    return appear(sceneRoot, startValues, visInfo.startVisibility,
                            endValues, visInfo.endVisibility);
                } else {
                    return disappear(sceneRoot, startValues, visInfo.startVisibility,
                            endValues, visInfo.endVisibility
                    );
                }
            }
        }
        return null;
    }

    /**
     * The default implementation of this method does nothing. Subclasses
     * should override if they need to set up anything prior to the
     * transition starting.
     *
     * @param sceneRoot
     * @param startValues
     * @param startVisibility
     * @param endValues
     * @param endVisibility
     * @return
     */
    protected Animator appear(ViewGroup sceneRoot,
            TransitionValues startValues, int startVisibility,
            TransitionValues endValues, int endVisibility) {
        return null;
    }

    /**
     * The default implementation of this method does nothing. Subclasses
     * should override if they need to set up anything prior to the
     * transition starting.
     *
     * @param sceneRoot
     * @param startValues
     * @param startVisibility
     * @param endValues
     * @param endVisibility
     * @return
     */
    protected Animator disappear(ViewGroup sceneRoot,
            TransitionValues startValues, int startVisibility,
            TransitionValues endValues, int endVisibility) {
        return null;
    }
}
