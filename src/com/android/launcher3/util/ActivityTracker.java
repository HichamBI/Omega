/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.util;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helper class to statically track activity creation
 * @param <T> The activity type to track
 */
public final class  ActivityTracker<T extends BaseActivity> {
    private static final String EXTRA_SCHEDULER_CALLBACK = "launcher.scheduler_callback";

    private WeakReference<T> mCurrentActivity = new WeakReference<>(null);
    private CopyOnWriteArrayList<SchedulerCallback<T>> mCallbacks = new CopyOnWriteArrayList<>();

    @Nullable
    public <R extends T> R getCreatedActivity() {
        return (R) mCurrentActivity.get();
    }

    public void onActivityDestroyed(T activity) {
        if (mCurrentActivity.get() == activity) {
            mCurrentActivity.clear();
        }
    }

    /**
     * Call {@link SchedulerCallback#init(BaseActivity, boolean)} when the
     * activity is ready. If the activity is already created, this is called immediately.
     * <p>
     * The tracker maintains a strong ref to the callback, so it is up to the caller to return
     * {@code false} in the callback OR to unregister the callback explicitly.
     *
     * @param callback The callback to call init() on when the activity is ready.
     */
    public void registerCallback(SchedulerCallback<T> callback) {
        T activity = mCurrentActivity.get();
        mCallbacks.add(callback);
        if (activity != null) {
            if (!callback.init(activity, activity.isStarted())) {
                unregisterCallback(callback);
            }
        }
    }

    /**
     * Unregisters a registered callback.
     */
    public void unregisterCallback(SchedulerCallback<T> callback) {
        mCallbacks.remove(callback);
    }

    public boolean handleCreate(T activity) {
        mCurrentActivity = new WeakReference<>(activity);
        return handleIntent(activity, false /* alreadyOnHome */);
    }

    public boolean handleNewIntent(T activity) {
        return handleIntent(activity, activity.isStarted());
    }

    private boolean handleIntent(T activity, boolean alreadyOnHome) {
        boolean handled = false;
        for (SchedulerCallback<T> cb : mCallbacks) {
            if (!cb.init(activity, alreadyOnHome)) {
                // Callback doesn't want any more updates
                unregisterCallback(cb);
            }
            handled = true;
        }
        return handled;
    }

    public interface SchedulerCallback<T extends BaseActivity> {

        /**
         * Called when the activity is ready.
         *
         * @param alreadyOnHome Whether the activity is already started.
         * @return Whether to continue receiving callbacks (i.e. if the activity is recreated).
         */
        boolean init(T activity, boolean alreadyOnHome);

        /**
         * Adds this callback as an extra on the intent, so we can retrieve it in handleIntent() and
         * call {@link #init}. The intent should be used to start the activity after calling this
         * method in order for us to get handleIntent().
         */
        default Intent addToIntent(Intent intent) {
            Bundle extras = new Bundle();
            extras.putBinder(EXTRA_SCHEDULER_CALLBACK, ObjectWrapper.wrap(this));
            intent.putExtras(extras);
            return intent;
        }
    }
}
