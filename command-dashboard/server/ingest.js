import express from 'express';
import cors from 'cors';

const app = express();
app.use(cors());
app.use(express.json());

let reports = [];

const SEVERITY_WEIGHT = {
  "LOW": 1,
  "MEDIUM": 2,
  "HIGH": 3,
  "CRITICAL": 4
};

function calculateWeight(report) {
  const baseSeverity = SEVERITY_WEIGHT[report.severity] || 1;
  const corrobs = report.corroborationCount || 0;
  return baseSeverity * Math.log(1 + corrobs);
}

app.post('/api/reports', (req, res) => {
  const data = req.body;
  if (!data.reportId) {
    return res.status(400).json({ error: 'reportId is required' });
  }

  // update or insert
  const existingIdx = reports.findIndex(r => r.reportId === data.reportId);
  if (existingIdx !== -1) {
    reports[existingIdx] = data;
  } else {
    reports.push(data);
  }

  res.json({ success: true });
});

app.get('/api/reports', (req, res) => {
  // Sort reports:
  // Primary key: baseSeverity * log(1 + corroborationCount) descending
  // Secondary key: TTL-remaining ascending
  const sortedReports = [...reports].sort((a, b) => {
    const weightA = calculateWeight(a);
    const weightB = calculateWeight(b);
    
    // Sort descending by weight
    if (Math.abs(weightA - weightB) > 0.0001) {
      return weightB - weightA;
    }
    
    // Sort ascending by TTL
    const ttlA = a.ttl || 0;
    const ttlB = b.ttl || 0;
    return ttlA - ttlB;
  });

  res.json(sortedReports);
});

const PORT = 3001;
app.listen(PORT, () => {
  console.log(`Ingest server running on port ${PORT}`);
});
