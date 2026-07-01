import {
  activityEvents,
  dashboardMetrics,
  familyProfiles,
  getCriticalServiceCount,
  getProtectedDeviceCount,
  getTopBlockedDomain,
  getTotalBlockedToday,
  networkCards,
  networkSegments,
  protectionModules,
  quickActions,
  recommendations,
  serviceHealth,
  threatPosture,
  topBlockedDomains,
  trafficSeries,
  type HealthLevel
} from './domain/dashboard';
import {
  deviceCenterCapabilities,
  deviceCenterEndpoints,
  deviceCenterRows,
  deviceDetailTabs,
  deviceDiscoverySettings,
  getDeviceCenterTotals,
  getSelectedDeviceDetail,
  type DeviceConnectionState,
  type DeviceProtectionMode
} from './domain/deviceCenter';
import {
  getLegacyParityTotals,
  legacyParityGroups,
  type LegacyParityStage
} from './domain/legacyParity';
import { t } from './i18n/messages';
import './App.css';

const navItems = [
  { id: 'dashboard', label: t('nav.dashboard'), icon: '⌁' },
  { id: 'devices', label: t('nav.devices'), icon: '◈' },
  { id: 'legacy-parity', label: t('nav.parity'), icon: '⇄' },
  { id: 'protection', label: t('nav.protection'), icon: '◆' },
  { id: 'network', label: t('nav.network'), icon: '◎' },
  { id: 'family', label: t('nav.family'), icon: '◌' },
  { id: 'system', label: t('nav.system'), icon: '⚙' }
] as const;

const maxRequests = Math.max(...trafficSeries.map((point) => point.requests));
const maxBlocked = Math.max(...trafficSeries.map((point) => point.blocked));
const requestPolyline = trafficSeries
  .map((point, index) => `${(index / (trafficSeries.length - 1)) * 100},${96 - (point.requests / maxRequests) * 82}`)
  .join(' ');
const blockedPolyline = trafficSeries
  .map((point, index) => `${(index / (trafficSeries.length - 1)) * 100},${96 - (point.blocked / maxBlocked) * 82}`)
  .join(' ');

function healthLabel(status: HealthLevel): string {
  if (status === 'online') return t('status.online');
  if (status === 'warning') return t('status.warning');
  return t('status.offline');
}

function protectionLabel(status: DeviceProtectionMode): string {
  if (status === 'protected') return t('status.protected');
  if (status === 'learning') return t('status.learning');
  if (status === 'paused') return t('status.paused');
  return t('status.disabled');
}

function connectionLabel(status: DeviceConnectionState): string {
  return status === 'online' ? t('status.online') : t('status.offline');
}

function formatNumber(value: number): string {
  return value.toLocaleString('de-DE');
}

function parityStageClass(stage: LegacyParityStage): string {
  return `stage-${stage}`;
}

function App() {
  const topDomain = getTopBlockedDomain();
  const parityTotals = getLegacyParityTotals();
  const deviceTotals = getDeviceCenterTotals();
  const selectedDevice = getSelectedDeviceDetail();

  return (
    <div className="app-frame">
      <aside className="sidebar" aria-label="eBlocker navigation">
        <div className="brand-block">
          <div className="brand-mark">eB</div>
          <div>
            <strong>eBlocker</strong>
            <span>Bookworm UI</span>
          </div>
        </div>

        <nav className="nav-list">
          {navItems.map((item, index) => (
            <a className={`nav-item ${index === 0 ? 'active' : ''}`} href={`#${item.id}`} key={item.id}>
              <span>{item.icon}</span>
              {item.label}
            </a>
          ))}
        </nav>

        <div className="sidebar-signal">
          <span className="signal-dot" />
          <strong>{threatPosture.score}% Schutzlage</strong>
          <small>{threatPosture.openWarnings} offene Hinweise · {getCriticalServiceCount()} kritisch</small>
        </div>

        <div className="sidebar-card">
          <span className="mini-label">{t('label.nextStep')}</span>
          <p>{t('copy.nextStep')}</p>
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div className="search-box" role="search">
            <span>⌕</span>
            <input aria-label={t('app.search')} placeholder={t('app.search')} />
          </div>
          <div className="topbar-actions">
            <span className="sync-pill">● {t('app.sync')}</span>
            <a className="ghost-button" href="/settings/">{t('action.openLegacy')}</a>
            <button className="primary-button" type="button">{t('action.reviewChanges')}</button>
          </div>
        </header>

        <section className="command-hero" id="dashboard">
          <div>
            <p className="eyebrow">{t('app.badge')}</p>
            <h1>{t('app.title')}</h1>
            <p className="hero-copy">{t('app.subtitle')}</p>
          </div>
          <div className="hero-status-strip">
            <span>{t('app.release')}</span>
            <strong>{getProtectedDeviceCount()} geschützt</strong>
            <small>{formatNumber(getTotalBlockedToday())} blockiert</small>
          </div>
        </section>

        <section className="panel legacy-parity-panel" id="legacy-parity">
          <div className="panel-header parity-header">
            <div>
              <span className="mini-label">{t('label.legacyModern')}</span>
              <h2>{t('section.parity.title')}</h2>
              <p>{t('section.parity.description')}</p>
            </div>
            <div className="parity-scoreboard">
              <span><b>{parityTotals.totalStates}</b> {t('label.legacyStates')}</span>
              <span><b>{parityTotals.mappedStates}</b> {t('label.mapped')}</span>
              <span><b>{parityTotals.coveragePercent}%</b> {t('label.parity')}</span>
            </div>
          </div>
          <div className="parity-groups">
            {legacyParityGroups.map((group) => (
              <article className={`parity-card ${parityStageClass(group.stage)} priority-${group.priority}`} key={group.id}>
                <div className="parity-card-top">
                  <div>
                    <strong>{group.title}</strong>
                    <small>{group.legacyModules.join(' · ')}</small>
                  </div>
                  <span>{group.legacyStateNames.length} {t('label.legacyStates')}</span>
                </div>
                <p className="legacy-copy">{t('label.old')}: {group.legacyDescription}</p>
                <p className="modern-copy">{t('label.new')}: {group.modernCoverage}</p>
                <div className="parity-card-footer">
                  <em>{group.stageLabel}</em>
                  <code>{group.targetSurfaces.join(' · ')}</code>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="mission-grid" aria-label="eBlocker Mission Control">
          <article className="panel posture-card">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.threat.title')}</h2>
                <p>{t('section.threat.description')}</p>
              </div>
              <span className="status-chip online">{threatPosture.label}</span>
            </div>
            <div className="posture-body">
              <div
                className="score-ring"
                style={{ background: `conic-gradient(#3ecf8e ${threatPosture.score * 3.6}deg, rgba(255,255,255,0.07) 0deg)` }}
                aria-label={`${threatPosture.score}%`}
              >
                <div>
                  <strong>{threatPosture.score}</strong>
                  <span>/100</span>
                </div>
              </div>
              <div className="posture-copy">
                <p>{threatPosture.summary}</p>
                <div className="posture-stats">
                  <span><b>{formatNumber(threatPosture.blockedRequests)}</b> blockiert</span>
                  <span><b>{formatNumber(threatPosture.inspectedConnections)}</b> geprüft</span>
                  <span><b>{threatPosture.openWarnings}</b> Hinweise</span>
                </div>
              </div>
            </div>
          </article>

          <article className="panel topology-card">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.topology.title')}</h2>
                <p>Gateway → LAN → Filter → Internet, als schneller Betriebsblick.</p>
              </div>
            </div>
            <div className="topology-flow">
              {networkSegments.map((segment, index) => (
                <div className="topology-node" key={segment.id}>
                  <span className={`status-dot ${segment.health}`} />
                  <strong>{segment.label}</strong>
                  <b>{segment.value}</b>
                  <small>{segment.detail}</small>
                  {index < networkSegments.length - 1 && <em>→</em>}
                </div>
              ))}
            </div>
          </article>
        </section>

        <section className="metrics-grid" aria-label={t('section.overview.title')}>
          {dashboardMetrics.map((metric) => (
            <article className={`metric-card tone-${metric.tone}`} key={metric.id}>
              <span>{metric.label}</span>
              <strong>{metric.value}</strong>
              <p>{metric.detail}</p>
              <small>{metric.trend}</small>
            </article>
          ))}
        </section>

        <section className="dashboard-grid telemetry-layout">
          <article className="panel telemetry-panel">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.telemetry.title')}</h2>
                <p>{t('section.telemetry.description')}</p>
              </div>
              <span className="mono-chip">Peak {getPeakLabel()}</span>
            </div>
            <svg className="traffic-chart" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="Traffic chart">
              <defs>
                <linearGradient id="trafficFill" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="rgba(62,207,142,0.28)" />
                  <stop offset="100%" stopColor="rgba(62,207,142,0)" />
                </linearGradient>
              </defs>
              <polyline className="chart-line requests" points={requestPolyline} />
              <polyline className="chart-line blocked" points={blockedPolyline} />
              <polygon className="chart-fill" points={`0,100 ${blockedPolyline} 100,100`} />
            </svg>
            <div className="chart-legend">
              <span><i className="legend requests" /> DNS-Anfragen</span>
              <span><i className="legend blocked" /> Blockiert</span>
              <span>{trafficSeries[0].hour}:00–{trafficSeries[trafficSeries.length - 1].hour}:00</span>
            </div>
          </article>

          <article className="panel top-domain-panel">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.topDomains.title')}</h2>
                <p>Stärkste Block-Treiber, nach Treffern sortiert.</p>
              </div>
            </div>
            <div className="domain-focus">
              <span>Top-Ziel</span>
              <strong>{topDomain.domain}</strong>
              <small>{formatNumber(topDomain.hits)} Treffer · {topDomain.category}</small>
            </div>
            <div className="domain-list">
              {topBlockedDomains.map((domain) => (
                <div className="domain-row" key={domain.domain}>
                  <div>
                    <strong>{domain.domain}</strong>
                    <span>{domain.category} · {domain.source}</span>
                  </div>
                  <b>{formatNumber(domain.hits)}</b>
                </div>
              ))}
            </div>
          </article>
        </section>

        <section className="dashboard-grid">
          <article className="panel large" id="system">
            <div className="panel-header">
              <div>
                <h2>{t('section.health.title')}</h2>
                <p>{t('section.health.description')}</p>
              </div>
              <span className="status-chip online">{t('status.active')}</span>
            </div>
            <div className="service-list">
              {serviceHealth.map((service) => (
                <div className="service-row" key={service.id}>
                  <span className={`status-dot ${service.status}`} />
                  <div>
                    <strong>{service.name}</strong>
                    <small>{service.detail}</small>
                  </div>
                  <em>{service.latency}</em>
                  <span className={`status-chip ${service.status}`}>{healthLabel(service.status)}</span>
                </div>
              ))}
            </div>
          </article>

          <article className="panel" id="protection">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.modules.title')}</h2>
                <p>{t('section.modules.description')}</p>
              </div>
            </div>
            <div className="module-list">
              {protectionModules.map((module) => (
                <div className="module-card" key={module.id}>
                  <div>
                    <strong>{module.name}</strong>
                    <p>{module.description}</p>
                  </div>
                  <div className="progress-line" aria-label={`${module.coverage}%`}>
                    <span style={{ width: `${module.coverage}%` }} />
                  </div>
                  <small>{module.coverage}% Abdeckung</small>
                </div>
              ))}
            </div>
          </article>
        </section>

        <section className="dashboard-grid two action-layout">
          <article className="panel recommendation-panel">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.recommendations.title')}</h2>
                <p>Priorisierte nächste Schritte, damit das Dashboard nicht nur zeigt, sondern führt.</p>
              </div>
            </div>
            <div className="recommendation-list">
              {recommendations.map((item) => (
                <div className={`recommendation-row priority-${item.priority}`} key={item.id}>
                  <span>{item.priority}</span>
                  <div>
                    <strong>{item.title}</strong>
                    <p>{item.detail}</p>
                    <small>{item.impact}</small>
                  </div>
                </div>
              ))}
            </div>
          </article>

          <article className="panel quick-panel">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.quickActions.title')}</h2>
                <p>Häufige Admin-Aktionen direkt erreichbar, später API-gebunden.</p>
              </div>
            </div>
            <div className="quick-action-grid">
              {quickActions.map((action) => (
                <button className={`quick-action ${action.tone}`} type="button" key={action.id}>
                  <strong>{action.label}</strong>
                  <span>{action.detail}</span>
                  <code>{action.targetApiPrefix}</code>
                </button>
              ))}
            </div>
          </article>
        </section>

        <section className="panel device-center-panel" id="devices">
          <div className="panel-header device-center-header">
            <div>
              <span className="mini-label">{t('label.deviceLegacyModern')}</span>
              <h2>{t('section.devices.title')}</h2>
              <p>{t('section.devices.description')}</p>
            </div>
            <div className="device-summary-grid">
              <span><b>{deviceTotals.total}</b> Geräte</span>
              <span><b>{deviceTotals.protected}</b> geschützt</span>
              <span><b>{deviceTotals.offline}</b> offline</span>
              <span><b>{deviceTotals.deletable}</b> löschbar</span>
            </div>
          </div>

          <div className="device-center-layout">
            <article className="device-list-card">
              <div className="device-list-toolbar">
                <div>
                  <strong>Geräteliste</strong>
                  <small>Alt: `deviceslist` · Suche, Sortierung, aktuelles Gerät, Offline-/No-IP-Auswahl</small>
                </div>
                <button className="ghost-button small" type="button">Aktuelles Gerät: {deviceTotals.currentDeviceName}</button>
              </div>
              <div className="device-modern-table" role="table" aria-label={t('section.devices.title')}>
                <div className="device-modern-row device-modern-head" role="row">
                  <span>{t('table.device')}</span>
                  <span>{t('table.ip')}</span>
                  <span>{t('table.profile')}</span>
                  <span>{t('table.protection')}</span>
                  <span>Flags</span>
                  <span>{t('table.status')}</span>
                </div>
                {deviceCenterRows.map((device) => (
                  <div className={`device-modern-row ${device.isCurrentDevice ? 'current' : ''}`} role="row" key={device.id}>
                    <span className="device-name"><b>{device.name}</b><small>{device.vendor} · {device.macAddress}</small></span>
                    <span className="mono">{device.ipAddresses.length > 0 ? device.ipAddresses.join(' · ') : 'Keine IP'}</span>
                    <span>{device.assignedProfile}<small>{device.assignedUser}</small></span>
                    <span><em className={`protection-badge ${device.protectionMode}`}>{protectionLabel(device.protectionMode)}</em></span>
                    <span className="device-flags">
                      {device.sslEnabled && <i>HTTPS</i>}
                      {device.mobileEnabled && <i>Mobile</i>}
                      {device.vpnActive && <i>VPN</i>}
                      {device.torActive && <i>Tor</i>}
                      {device.deletable && <i>Löschbar</i>}
                    </span>
                    <span><em className={`status-chip ${device.connectionState}`}>{connectionLabel(device.connectionState)}</em><small>{device.lastSeen}</small></span>
                  </div>
                ))}
              </div>
            </article>

            <aside className="device-side-stack">
              <article className="device-discovery-card">
                <div className="panel-header compact">
                  <div>
                    <h3>Discovery & Scan</h3>
                    <p>Alt: `devicesdiscovery` · Scan-Intervall, manueller Scan und Auto-Aktivierung.</p>
                  </div>
                </div>
                <div className="discovery-grid">
                  <span><b>{deviceDiscoverySettings.scanIntervalSeconds}s</b> Scan-Intervall</span>
                  <span><b>{deviceDiscoverySettings.scanningAvailable ? 'Ja' : 'Nein'}</b> Scan verfügbar</span>
                  <span><b>{deviceDiscoverySettings.autoEnableNewDevices ? 'An' : 'Aus'}</b> Auto-Aktivierung</span>
                  <span><b>{deviceDiscoverySettings.lastManualScan}</b> letzter Scan</span>
                </div>
                <div className="device-action-row">
                  <button className="primary-button" type="button">Netzwerk scannen</button>
                  <button className="ghost-button" type="button">Auto-Aktivierung ändern</button>
                </div>
              </article>

              <article className="device-api-card">
                <h3>API-Migration</h3>
                <p>Jeder alte DeviceService-Endpunkt hat einen neuen `/api/v1/devices`-Zielpfad.</p>
                <div className="endpoint-list">
                  {deviceCenterEndpoints.map((endpoint) => (
                    <div className="endpoint-row" key={endpoint.id}>
                      <span>{endpoint.method}</span>
                      <code>{endpoint.legacyPath}</code>
                      <b>→</b>
                      <code>{endpoint.modernPath}</code>
                    </div>
                  ))}
                </div>
              </article>
            </aside>
          </div>

          <div className="device-detail-grid">
            <article className="device-detail-card">
              <div className="panel-header compact">
                <div>
                  <h3>Details: {selectedDevice.name}</h3>
                  <p>Alt: `devicedetails` mit Tabs für Gerät, Benutzer, Anonymisierung, Mobile, Filter, HTTPS, Icon und Nachrichten.</p>
                </div>
                <span className="status-chip online">{selectedDevice.enabledActions.length} Aktionen</span>
              </div>
              <div className="detail-panel-grid">
                {selectedDevice.detailPanels.map((panel) => (
                  <div className="detail-panel" key={panel.id}>
                    <strong>{panel.label}</strong>
                    <p>{panel.value}</p>
                    <button className="ghost-button small" type="button">{panel.action}</button>
                  </div>
                ))}
              </div>
            </article>

            <article className="device-tabs-card">
              <h3>Legacy-Tabs abgedeckt</h3>
              <div className="detail-tab-list">
                {deviceDetailTabs.map((tab) => (
                  <div className="detail-tab-row" key={tab.id}>
                    <strong>{tab.label}</strong>
                    <span>{tab.modernIntent}</span>
                    <code>{tab.legacyTemplate}</code>
                  </div>
                ))}
              </div>
            </article>
          </div>

          <div className="device-capability-strip">
            {deviceCenterCapabilities.map((capability) => (
              <span key={capability}>{capability}</span>
            ))}
          </div>
        </section>

        <section className="dashboard-grid two">
          <article className="panel" id="network">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.network.title')}</h2>
                <p>{t('section.network.description')}</p>
              </div>
            </div>
            <div className="network-grid">
              {networkCards.map((card) => (
                <div className="network-card" key={card.id}>
                  <span>{card.label}</span>
                  <strong>{card.value}</strong>
                  <p>{card.detail}</p>
                </div>
              ))}
            </div>
          </article>

          <article className="panel" id="family">
            <div className="panel-header compact">
              <div>
                <h2>{t('section.family.title')}</h2>
                <p>{t('section.family.description')}</p>
              </div>
              <button className="ghost-button small" type="button">{t('action.newProfile')}</button>
            </div>
            <div className="profile-list">
              {familyProfiles.map((profile) => (
                <div className="profile-row" key={profile.id}>
                  <div>
                    <strong>{profile.name}</strong>
                    <span>{profile.devices} Geräte · {profile.schedule}</span>
                  </div>
                  <em>{profile.level}</em>
                </div>
              ))}
            </div>
          </article>
        </section>

        <section className="panel activity-panel">
          <div className="panel-header compact">
            <div>
              <h2>{t('section.activity.title')}</h2>
              <p>{t('section.activity.description')}</p>
            </div>
          </div>
          <div className="activity-list">
            {activityEvents.map((event) => (
              <div className={`activity-row ${event.tone}`} key={event.id}>
                <time>{event.time}</time>
                <div>
                  <strong>{event.title}</strong>
                  <p>{event.detail}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function getPeakLabel(): string {
  const peak = trafficSeries.reduce((highest, point) => point.blocked > highest.blocked ? point : highest, trafficSeries[0]);
  return `${peak.hour}:00 · ${formatNumber(peak.blocked)} blockiert`;
}

export default App;
