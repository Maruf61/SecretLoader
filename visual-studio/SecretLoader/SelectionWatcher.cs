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

using System;
using Microsoft.VisualStudio;
using Microsoft.VisualStudio.Shell.Interop;

namespace SecretLoader
{
    /// <summary>Fires a callback when the solution's startup project changes (to refresh the status widget).</summary>
    internal sealed class SelectionWatcher : IVsSelectionEvents
    {
        private readonly Action _onStartupProjectChanged;

        public SelectionWatcher(Action onStartupProjectChanged) => _onStartupProjectChanged = onStartupProjectChanged;

        public int OnElementValueChanged(uint elementid, object varValueOld, object varValueNew)
        {
            if (elementid == (uint)VSConstants.VSSELELEMID.SEID_StartupProject) _onStartupProjectChanged();
            return VSConstants.S_OK;
        }

        public int OnSelectionChanged(IVsHierarchy pHierOld, uint itemidOld, IVsMultiItemSelect pMISOld,
            ISelectionContainer pSCOld, IVsHierarchy pHierNew, uint itemidNew, IVsMultiItemSelect pMISNew,
            ISelectionContainer pSCNew) => VSConstants.S_OK;

        public int OnCmdUIContextChanged(uint dwCmdUICookie, int fActive) => VSConstants.S_OK;
    }
}
