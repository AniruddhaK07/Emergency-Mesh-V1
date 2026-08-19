import { useEffect, useState, useMemo } from 'react';
import { 
  AlertTriangle, 
  Clock, 
  MapPin, 
  Users, 
  Activity, 
  Flame, 
  ShieldAlert, 
  ArrowUpDown, 
  Filter, 
  Trash2, 
  Radio, 
  Navigation
} from 'lucide-react';

interface Report {
  reportId: string;
  emergencyType: "TRAPPED" | "INJURED" | "FIRE" | "NEED_EVAC";
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  casualtyCount: number;
  notes: string;
  timestamp: number;
  hasLocation?: boolean;
  latitude: number;
  longitude: number;
  corroborationCount: number;
  ttl: number;
}

type SortOption = 'latest' | 'priority' | 'oldest' | 'casualties' | 'corroboration';
type SeverityFilter = 'ALL' | 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
type TypeFilter = 'ALL' | 'TRAPPED' | 'INJURED' | 'FIRE' | 'NEED_EVAC';

function calculateWeight(report: Report) {
  const severities = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 };
  const baseSeverity = severities[report.severity] || 1;
  return baseSeverity * Math.log(1 + (report.corroborationCount || 0));
}

function timeSince(timestamp: number) {
  const seconds = Math.floor((Date.now() - timestamp) / 1000);
  if (seconds < 5) return "just now";
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

function formatExactTime(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

const TypeIcon = ({ type }: { type: string }) => {
  switch (type) {
    case 'FIRE': return <Flame className="w-5 h-5 text-orange-500" />;
    case 'TRAPPED': return <AlertTriangle className="w-5 h-5 text-amber-400" />;
    case 'INJURED': return <Activity className="w-5 h-5 text-red-400" />;
    case 'NEED_EVAC': return <ShieldAlert className="w-5 h-5 text-cyan-400" />;
    default: return <AlertTriangle className="w-5 h-5" />;
  }
};

export default function App() {
  const [reports, setReports] = useState<Report[]>([]);
  const [connected, setConnected] = useState(false);
  const [sortBy, setSortBy] = useState<SortOption>('latest');
  const [severityFilter, setSeverityFilter] = useState<SeverityFilter>('ALL');
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
  const [lastRefreshed, setLastRefreshed] = useState<Date>(new Date());
  const [isClearing, setIsClearing] = useState(false);

  const fetchReports = async () => {
    try {
      const res = await fetch('http://localhost:3001/api/reports');
      if (res.ok) {
        const data = await res.json();
        setReports(data);
        setConnected(true);
        setLastRefreshed(new Date());
      } else {
        setConnected(false);
      }
    } catch (err) {
      setConnected(false);
    }
  };

  useEffect(() => {
    fetchReports();
    const interval = setInterval(fetchReports, 3000);
    return () => clearInterval(interval);
  }, []);

  const handleClearAll = async () => {
    if (!window.confirm("Clear all reports from the server?")) return;
    setIsClearing(true);
    try {
      await fetch('http://localhost:3001/api/reports', { method: 'DELETE' });
      setReports([]);
    } catch (e) {
      console.error("Failed to clear reports", e);
    } finally {
      setIsClearing(false);
    }
  };

  // Filter and sort reports in memory
  const processedReports = useMemo(() => {
    let result = [...reports];

    // Filter by Severity
    if (severityFilter !== 'ALL') {
      result = result.filter(r => r.severity === severityFilter);
    }

    // Filter by Type
    if (typeFilter !== 'ALL') {
      result = result.filter(r => r.emergencyType === typeFilter);
    }

    // Sort
    result.sort((a, b) => {
      switch (sortBy) {
        case 'latest':
          return b.timestamp - a.timestamp;
        case 'oldest':
          return a.timestamp - b.timestamp;
        case 'priority': {
          const weightDiff = calculateWeight(b) - calculateWeight(a);
          if (Math.abs(weightDiff) > 0.001) return weightDiff;
          return b.timestamp - a.timestamp;
        }
        case 'casualties':
          return (b.casualtyCount || 0) - (a.casualtyCount || 0);
        case 'corroboration':
          return (b.corroborationCount || 0) - (a.corroborationCount || 0);
        default:
          return b.timestamp - a.timestamp;
      }
    });

    return result;
  }, [reports, sortBy, severityFilter, typeFilter]);

  const stats = {
    total: reports.length,
    critical: reports.filter(r => r.severity === 'CRITICAL').length,
    high: reports.filter(r => r.severity === 'HIGH').length,
    medium: reports.filter(r => r.severity === 'MEDIUM').length,
    low: reports.filter(r => r.severity === 'LOW').length,
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-gray-300 p-4 lg:p-6 font-mono">
      {/* Header */}
      <header className="flex flex-wrap justify-between items-center border-b border-neutral-800 pb-4 mb-6 gap-3">
        <div className="flex items-center gap-3">
          <div className="bg-red-500/10 p-2 border border-red-500/30 rounded">
            <Radio className="w-6 h-6 text-red-500 animate-pulse" />
          </div>
          <div>
            <h1 className="text-xl font-black uppercase tracking-widest text-white">Emergency Mesh Command</h1>
            <p className="text-xs text-neutral-500">Tier 3 Operational Incident Dashboard</p>
          </div>
        </div>
        
        <div className="flex items-center gap-4 text-xs">
          <div className="flex items-center gap-2 bg-neutral-900 border border-neutral-800 px-3 py-1.5 rounded">
            <span className={`w-2.5 h-2.5 rounded-full ${connected ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.6)]' : 'bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.6)]'}`}></span>
            <span className="font-semibold text-neutral-300">{connected ? 'MESH ONLINE' : 'DISCONNECTED'}</span>
          </div>

          <button 
            onClick={handleClearAll}
            disabled={isClearing || reports.length === 0}
            className="flex items-center gap-1.5 bg-red-950/40 hover:bg-red-900/60 border border-red-800/50 text-red-400 px-3 py-1.5 rounded transition disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
            title="Clear all stored reports"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span>CLEAR ALL</span>
          </button>
        </div>
      </header>

      {/* Stats Summary Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
        <div className="bg-neutral-900/80 border border-neutral-800 p-3.5 rounded">
          <div className="text-neutral-500 text-[11px] font-semibold tracking-wider mb-1">TOTAL REPORTS</div>
          <div className="text-2xl font-bold text-white">{stats.total}</div>
        </div>
        <div className="bg-neutral-900/80 border border-red-900/40 p-3.5 rounded">
          <div className="text-red-400 text-[11px] font-semibold tracking-wider mb-1">CRITICAL</div>
          <div className="text-2xl font-bold text-red-500">{stats.critical}</div>
        </div>
        <div className="bg-neutral-900/80 border border-orange-900/40 p-3.5 rounded">
          <div className="text-orange-400 text-[11px] font-semibold tracking-wider mb-1">HIGH SEVERITY</div>
          <div className="text-2xl font-bold text-orange-400">{stats.high}</div>
        </div>
        <div className="bg-neutral-900/80 border border-neutral-800 p-3.5 rounded">
          <div className="text-neutral-400 text-[11px] font-semibold tracking-wider mb-1">MED / LOW</div>
          <div className="text-2xl font-bold text-neutral-300">{stats.medium + stats.low}</div>
        </div>
      </div>

      {/* Controls & Filter Toolbar */}
      <div className="bg-neutral-900 border border-neutral-800 p-4 rounded mb-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          
          {/* Sorting Control */}
          <div className="flex items-center gap-2">
            <ArrowUpDown className="w-4 h-4 text-cyan-400" />
            <span className="text-xs font-semibold text-neutral-400">SORT BY:</span>
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortOption)}
              className="bg-neutral-950 border border-neutral-700 text-white text-xs px-3 py-1.5 rounded focus:outline-none focus:border-cyan-500 cursor-pointer font-mono"
            >
              <option value="latest">⚡ Most Recent First (Default)</option>
              <option value="priority">🚨 Priority Weight (Critical First)</option>
              <option value="oldest">⏳ Oldest First</option>
              <option value="casualties">👥 Casualties (High to Low)</option>
              <option value="corroboration">🔄 Corroborations (High to Low)</option>
            </select>
          </div>

          {/* Quick Info */}
          <div className="text-xs text-neutral-500">
            Showing <span className="text-white font-bold">{processedReports.length}</span> of <span className="text-white font-bold">{reports.length}</span> reports (Updated {timeSince(lastRefreshed.getTime())})
          </div>
        </div>

        {/* Filters Row */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-3 border-t border-neutral-800 text-xs">
          {/* Severity Filter */}
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-neutral-500 font-semibold mr-1 flex items-center gap-1">
              <Filter className="w-3 h-3" /> SEVERITY:
            </span>
            {(['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as SeverityFilter[]).map((s) => (
              <button
                key={s}
                onClick={() => setSeverityFilter(s)}
                className={`px-2.5 py-1 rounded text-[11px] font-bold tracking-wide transition cursor-pointer ${
                  severityFilter === s
                    ? s === 'CRITICAL' ? 'bg-red-600 text-white shadow-[0_0_8px_rgba(220,38,38,0.5)]'
                      : s === 'HIGH' ? 'bg-orange-600 text-white'
                      : 'bg-cyan-600 text-white'
                    : 'bg-neutral-950 border border-neutral-800 text-neutral-400 hover:border-neutral-700'
                }`}
              >
                {s}
              </button>
            ))}
          </div>

          {/* Type Filter */}
          <div className="flex items-center gap-1.5 flex-wrap md:justify-end">
            <span className="text-neutral-500 font-semibold mr-1">TYPE:</span>
            {(['ALL', 'TRAPPED', 'INJURED', 'FIRE', 'NEED_EVAC'] as TypeFilter[]).map((t) => (
              <button
                key={t}
                onClick={() => setTypeFilter(t)}
                className={`px-2.5 py-1 rounded text-[11px] font-bold tracking-wide transition cursor-pointer ${
                  typeFilter === t
                    ? 'bg-neutral-200 text-black shadow-sm'
                    : 'bg-neutral-950 border border-neutral-800 text-neutral-400 hover:border-neutral-700'
                }`}
              >
                {t}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Reports Feed */}
      <div className="space-y-3">
        {processedReports.map((report) => {
          const weight = calculateWeight(report).toFixed(2);
          const isCritical = report.severity === 'CRITICAL';
          const isHigh = report.severity === 'HIGH';
          const isRecent = (Date.now() - report.timestamp) < 60000; // Received < 60s ago
          
          return (
            <div 
              key={report.reportId} 
              className={`border p-4 bg-neutral-900 rounded-sm transition flex flex-col gap-3 relative ${
                isCritical 
                  ? 'border-red-600/70 bg-gradient-to-r from-red-950/20 to-neutral-900 shadow-[0_0_15px_rgba(239,68,68,0.15)]' 
                  : isHigh 
                  ? 'border-orange-600/50 bg-gradient-to-r from-orange-950/15 to-neutral-900' 
                  : 'border-neutral-800'
              }`}
            >
              {/* Top Banner */}
              <div className="flex justify-between items-start">
                <div className="flex items-center gap-3">
                  <div className={`p-2.5 rounded-sm ${
                    isCritical ? 'bg-red-500/20 border border-red-500/40' : 
                    isHigh ? 'bg-orange-500/20 border border-orange-500/40' : 
                    'bg-neutral-800 border border-neutral-700'
                  }`}>
                    <TypeIcon type={report.emergencyType} />
                  </div>
                  <div>
                    <div className="font-bold text-base text-white flex items-center gap-2">
                      <span>{report.emergencyType}</span>
                      
                      <span className={`text-[10px] uppercase font-black px-2 py-0.5 rounded ${
                        isCritical ? 'bg-red-600 text-white' : 
                        isHigh ? 'bg-orange-600 text-white' : 
                        'bg-neutral-800 text-neutral-300'
                      }`}>
                        {report.severity}
                      </span>

                      {isRecent && (
                        <span className="text-[10px] bg-green-500/20 border border-green-500/50 text-green-400 font-bold px-1.5 py-0.5 rounded animate-pulse">
                          NEW
                        </span>
                      )}
                    </div>
                    
                    <div className="text-[11px] text-neutral-500 font-mono mt-0.5">
                      REPORT ID: <span className="text-neutral-400">{report.reportId}</span>
                    </div>
                  </div>
                </div>
                
                {/* Time & Priority */}
                <div className="text-right">
                  <div className="text-xs text-neutral-300 font-semibold flex items-center gap-1.5 justify-end">
                    <Clock className="w-3.5 h-3.5 text-neutral-400" />
                    <span>{timeSince(report.timestamp)}</span>
                    <span className="text-[10px] text-neutral-500 font-normal">({formatExactTime(report.timestamp)})</span>
                  </div>
                  <div className="text-[10px] text-neutral-500 mt-1 font-mono">
                    PRIORITY WEIGHT: <span className="text-cyan-400 font-bold">{weight}</span>
                  </div>
                </div>
              </div>

              {/* Data Badges Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs border-t border-neutral-800/80 pt-3">
                <div className="flex items-center gap-2 text-neutral-400 bg-neutral-950/60 px-2.5 py-1.5 rounded border border-neutral-800/50">
                  <Users className="w-3.5 h-3.5 text-neutral-400" />
                  <span><strong className="text-white">{report.casualtyCount}</strong> CASUALTIES</span>
                </div>
                
                <div className="flex items-center gap-2 text-neutral-400 bg-neutral-950/60 px-2.5 py-1.5 rounded border border-neutral-800/50">
                  <Activity className="w-3.5 h-3.5 text-cyan-400" />
                  <span><strong className="text-cyan-300">{report.corroborationCount}</strong> CORROBORATION</span>
                </div>
                
                <div className="flex items-center gap-2 text-neutral-400 bg-neutral-950/60 px-2.5 py-1.5 rounded border border-neutral-800/50">
                  <MapPin className="w-3.5 h-3.5 text-amber-400" />
                  <span className="truncate">
                    {report.hasLocation !== false ? (
                      <span className="text-neutral-200">{report.latitude.toFixed(4)}, {report.longitude.toFixed(4)}</span>
                    ) : (
                      <span className="text-amber-400 italic">NULL_LOC (RELAY BOUND)</span>
                    )}
                  </span>
                </div>
                
                <div className="flex items-center gap-2 text-neutral-400 bg-neutral-950/60 px-2.5 py-1.5 rounded border border-neutral-800/50 justify-between">
                  <div className="flex items-center gap-1">
                    <Navigation className="w-3 h-3 text-neutral-500" />
                    <span>HOPS:</span>
                  </div>
                  <span className="text-neutral-200 font-bold bg-neutral-800 px-1.5 py-0.5 rounded text-[10px]">TTL {report.ttl}</span>
                </div>
              </div>

              {/* Decrypted Notes */}
              {report.notes && (
                <div className="text-xs text-neutral-200 bg-neutral-950/90 p-3 border border-neutral-800 rounded font-mono">
                  <div className="text-[10px] text-neutral-500 uppercase font-bold tracking-wider mb-1">Decrypted Voice/Text Notes:</div>
                  <div className="whitespace-pre-wrap">{report.notes}</div>
                </div>
              )}
            </div>
          );
        })}
        
        {processedReports.length === 0 && (
          <div className="text-center text-neutral-500 py-16 border border-neutral-800/80 border-dashed rounded bg-neutral-900/30">
            <div className="text-sm font-semibold">No emergency reports match the current filter</div>
            <div className="text-xs text-neutral-600 mt-1">Listening for incoming mesh broadcasts...</div>
          </div>
        )}
      </div>
    </div>
  );
}
