/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saggitt.omega.preferences

import android.content.Context
import android.util.AttributeSet
import com.saggitt.omega.settings.DecorLayout
import com.saggitt.omega.views.InsettableRecyclerView

class PreferenceRecyclerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : InsettableRecyclerView(context, attrs, defStyleAttr) {

    private var elevationHelper: DecorLayout.ToolbarElevationHelper? = null
        set(value) {
            field?.destroy()
            field = value
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        elevationHelper = DecorLayout.ToolbarElevationHelper(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        elevationHelper = null
    }
}