import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ErrorBoundary } from './components/ErrorBoundary'
import AppShell from './components/layout/AppShell'
import DashboardPage from './pages/DashboardPage'
import PatientsPage from './pages/PatientsPage'
import PatientDetailPage from './pages/PatientDetailPage'
import PredictionsPage from './pages/PredictionsPage'
import ClinicalInsightsPage from './pages/ClinicalInsightsPage'
import DigitalTwinPage from './pages/DigitalTwinPage'
import WearableMonitoringPage from './pages/WearableMonitoringPage'
import SimulationsPage from './pages/SimulationsPage'
import AuditPage from './pages/AuditPage'
import SettingsPage from './pages/SettingsPage'

export default function App() {
  return (
    <BrowserRouter>
      <ErrorBoundary>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/patients" element={<PatientsPage />} />
            <Route path="/patients/:patientId" element={<PatientDetailPage />} />
            <Route path="/predictions" element={<PredictionsPage />} />
            <Route path="/insights" element={<ClinicalInsightsPage />} />
            <Route path="/twin" element={<DigitalTwinPage />} />
            <Route path="/wearables" element={<WearableMonitoringPage />} />
            <Route path="/simulations" element={<SimulationsPage />} />
            {/* Backward compatibility with /workspace/:section routes */}
            <Route path="/workspace/predictions" element={<PredictionsPage />} />
            <Route path="/workspace/insights" element={<ClinicalInsightsPage />} />
            <Route path="/workspace/twin" element={<DigitalTwinPage />} />
            <Route path="/workspace/wearables" element={<WearableMonitoringPage />} />
            <Route path="/workspace/simulations" element={<SimulationsPage />} />
            <Route path="/audit" element={<AuditPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
          {/* Catch-all redirect to dashboard */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </ErrorBoundary>
    </BrowserRouter>
  )
}
