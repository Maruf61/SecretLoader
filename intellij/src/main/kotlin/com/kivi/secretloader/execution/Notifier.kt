/*
 * SecretLoader
 * Copyright (C) 2026 Kivi A.Ş.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kivi.secretloader.execution

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/** Balloon notifications (never modal dialogs on the launch hot-path) — counts only, never values. */
object Notifier {
    private const val GROUP = "SecretLoader"

    fun info(project: Project, content: String, title: String = "SecretLoader") =
        notify(project, title, content, NotificationType.INFORMATION)

    fun warn(project: Project, content: String, title: String = "SecretLoader") =
        notify(project, title, content, NotificationType.WARNING)

    fun error(project: Project, content: String, title: String = "SecretLoader") =
        notify(project, title, content, NotificationType.ERROR)

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP)
            ?.createNotification(title, content, type)
            ?.notify(project)
    }
}
