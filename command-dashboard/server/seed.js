import fetch from 'node-fetch';

// Sample data
const seedReports = [
  {
    reportId: "uuid-1",
    emergencyType: "FIRE",
    severity: "CRITICAL",
    casualtyCount: 2,
    notes: "Fire spreading fast in north wing",
    timestamp: Date.now() - 120000,
    latitude: 37.7749,
    longitude: -122.4194,
    corroborationCount: 15,
    ttl: 3
  },
  {
    reportId: "uuid-2",
    emergencyType: "INJURED",
    severity: "MEDIUM",
    casualtyCount: 1,
    notes: "Broken leg, conscious",
    timestamp: Date.now() - 360000,
    latitude: 37.7750,
    longitude: -122.4180,
    corroborationCount: 2,
    ttl: 5
  },
  {
    reportId: "uuid-3",
    emergencyType: "TRAPPED",
    severity: "HIGH",
    casualtyCount: 4,
    notes: "Trapped under rubble",
    timestamp: Date.now() - 600000,
    latitude: 37.7760,
    longitude: -122.4190,
    corroborationCount: 5,
    ttl: 4
  },
  {
    reportId: "uuid-4",
    emergencyType: "NEED_EVAC",
    severity: "LOW",
    casualtyCount: 10,
    notes: "Gathered at rally point, need transport",
    timestamp: Date.now() - 1800000,
    latitude: 37.7740,
    longitude: -122.4200,
    corroborationCount: 20,
    ttl: 2
  }
];

async function seed() {
  for (const report of seedReports) {
    try {
      await fetch('http://localhost:3001/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(report)
      });
      console.log(`Seeded report ${report.reportId}`);
    } catch (e) {
      console.error(`Failed to seed ${report.reportId}:`, e.message);
    }
  }
}

seed();
