import { useEffect, useState } from 'react';
import { AlertTriangle, Clock, MapPin, Users, Activity, Flame, ShieldAlert } from 'lucide-react';

interface Report {
  reportId: string;
  emergencyType: "TRAPPED" | "INJURED" | "FIRE" | "NEED_EVAC";
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  casualtyCount: number;
  notes: string;
  timestamp: number;
  latitude: number;
  longitude: number;
  corroborationCount: number;
  ttl: number;
}

function calculateWeight(report: Report) {
  const severities = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 };
  const baseSeverity = severities[report.severity] || 1;
  return baseSeverity * Math.log(1 + (report.corroborationCount || 0));
}

function timeSince(timestamp: number) {
  const seconds = Math.floor((Date.now() - timestamp) / 1000);
  let interval = seconds / 31536000;
  if (interval > 1) return Math.floor(interval) + " yr ago";
  interval = seconds / 2592000;
  if (interval > 1) return Math.floor(interval) + " mo ago";
  interval = seconds / 86400;
  if (interval > 1) return Math.floor(interval) + " d ago";
  interval = seconds / 3600;
  if (interval > 1) return Math.floor(interval) + " hr ago";
  interval = seconds / 60;
  if (interval > 1) return Math.floor(interval) + " min ago";
  return Math.floor(seconds) + " s ago";
}

const TypeIcon = ({ type }: { type: string }) => {
  switch (type) {
    case 'FIRE': return <Flame className="w-5 h-5" />;
    case 'TRAPPED': return <AlertTriangle className="w-5 h-5" />;
    case 'INJURED': return <Activity className="w-5 h-5" />;
    case 'NEED_EVAC': return <ShieldAlert className="w-5 h-5" />;
    default: return <AlertTriangle className="w-5 h-5" />;
  }
}

export default function App() {
  const [reports, setReports] = useState<Report[]>([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const fetchReports = async () => {
      try {
        const res = await fetch('http://localhost:3001/api/reports');
        if (res.ok) {
          const data = await res.json();
          setReports(data);
          setConnected(true);
        } else {
          setConnected(false);
        }
      } catch (err) {
        setConnected(false);
      }
    };

    fetchReports();
    const interval = setInterval(fetchReports, 5000);
    return () => clearInterval(interval);
  }, []);

  const stats = {
    total: reports.length,
    critical: reports.filter(r => r.severity === 'CRITICAL').length,
    high: reports.filter(r => r.severity === 'HIGH').length,
  };

  return (
    <div className="min-h-screen bg-black text-gray-300 p-4 font-mono">
      {/* Header */}
      <header className="flex justify-between items-center border-b border-gray-800 pb-4 mb-6">
        <h1 className="text-xl font-bold uppercase tracking-widest text-white">Emergency Mesh Command</h1>
        <div className="flex items-center gap-2 text-sm">
          <span className={`w-3 h-3 rounded-full ${connected ? 'bg-green-500' : 'bg-red-500'}`}></span>
          {connected ? 'LIVE (PORT 3001)' : 'DISCONNECTED'}
        </div>
      </header>

      {/* StatsBar */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="bg-gray-900 border border-gray-800 p-4">
          <div className="text-gray-500 text-xs mb-1">TOTAL REPORTS</div>
          <div className="text-2xl font-bold text-white">{stats.total}</div>
        </div>
        <div className="bg-gray-900 border border-gray-800 p-4">
          <div className="text-gray-500 text-xs mb-1">CRITICAL INCIDENTS</div>
          <div className="text-2xl font-bold text-critical">{stats.critical}</div>
        </div>
        <div className="bg-gray-900 border border-gray-800 p-4">
          <div className="text-gray-500 text-xs mb-1">HIGH SEVERITY</div>
          <div className="text-2xl font-bold text-gray-200">{stats.high}</div>
        </div>
      </div>

      {/* ReportList */}
      <div className="space-y-4">
        <div className="text-xs text-gray-500 mb-2 uppercase tracking-wide">
          Reports sorted by operational priority
        </div>
        
        {reports.map((report) => {
          const weight = calculateWeight(report).toFixed(2);
          const isCritical = report.severity === 'CRITICAL';
          
          return (
            <div key={report.reportId} 
                 className={`border p-4 bg-gray-900 flex flex-col gap-3 ${
                   isCritical ? 'border-critical/50 shadow-[0_0_15px_rgba(239,68,68,0.1)]' : 'border-gray-800'
                 }`}>
              
              <div className="flex justify-between items-start">
                <div className="flex items-center gap-3">
                  <div className={`p-2 rounded-sm ${isCritical ? 'bg-critical/20 text-critical' : 'bg-gray-800 text-gray-400'}`}>
                    <TypeIcon type={report.emergencyType} />
                  </div>
                  <div>
                    <div className="font-bold text-lg text-white flex items-center gap-2">
                      {report.emergencyType}
                      <span className={`text-xs px-2 py-0.5 rounded-sm ${
                        isCritical ? 'bg-critical text-black font-bold' : 'bg-gray-800 text-gray-300'
                      }`}>
                        {report.severity}
                      </span>
                    </div>
                    <div className="text-xs text-gray-500 font-mono mt-1">
                      ID: {report.reportId.split('-')[0]}...
                    </div>
                  </div>
                </div>
                
                <div className="text-right">
                  <div className="text-xs text-gray-400 flex items-center gap-1 justify-end">
                    <Clock className="w-3 h-3" />
                    {timeSince(report.timestamp)}
                  </div>
                  <div className="text-[10px] text-gray-600 mt-1">
                    PRIORITY WGT: {weight}
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-4 gap-2 text-sm mt-2 border-t border-gray-800 pt-3">
                <div className="flex items-center gap-2 text-gray-400">
                  <Users className="w-4 h-4" />
                  <span>{report.casualtyCount} CASUALTIES</span>
                </div>
                <div className="flex items-center gap-2 text-gray-400">
                  <Activity className="w-4 h-4" />
                  <span>{report.corroborationCount} CORROB</span>
                </div>
                <div className="flex items-center gap-2 text-gray-400">
                  <MapPin className="w-4 h-4" />
                  <span>{report.latitude.toFixed(4)}, {report.longitude.toFixed(4)}</span>
                </div>
                <div className="flex items-center gap-2 text-gray-400 justify-end">
                  <span className="text-xs bg-gray-800 px-2 py-1">TTL: {report.ttl}</span>
                </div>
              </div>

              {report.notes && (
                <div className="mt-2 text-sm text-gray-300 bg-black/50 p-3 border border-gray-800 rounded-sm">
                  {report.notes}
                </div>
              )}
            </div>
          );
        })}
        
        {reports.length === 0 && (
          <div className="text-center text-gray-500 py-10 border border-gray-800 border-dashed">
            No incoming reports...
          </div>
        )}
      </div>
    </div>
  );
}
