package com.timelock.app.ui.permission

import androidx.lifecycle.ViewModel
import com.timelock.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 权限门 ViewModel —— 仅用于 Hilt 注入 [PermissionHelper]。
 */
@HiltViewModel
class PermissionGateViewModel @Inject constructor(
    val permissionHelper: PermissionHelper
) : ViewModel()
