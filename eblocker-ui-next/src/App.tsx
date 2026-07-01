import {
  activityEvents,
  dashboardMetrics,
  familyProfiles,
  getCriticalServiceCount,
  getProtectedDeviceCount,
  getTopBlockedDomain,
  getTotalBlockedToday,
  networkSegments,
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
import {
  dnsLocalRecords,
  dnsResolverModes,
  dnsServerRows,
  getDnsServerRatingTotals,
  getNetworkCenterTotals,
  networkCenterCapabilities,
  networkCenterEndpoints,
  networkDhcpServers,
  networkIpv4Config,
  networkIpv6Config,
  networkWizardFlows
} from './domain/networkCenter';
import {
  advancedPrivacySettings,
  analysisRecorder,
  doctorProbeRows,
  filterListRows,
  getBlockedProtectionTotal,
  getProtectionCenterTotals,
  protectionCenterCapabilities,
  protectionCenterEndpoints,
  protectionModulesModern,
  torExitPolicy
} from './domain/protectionCenter';
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
  const networkTotals = getNetworkCenterTotals();
  const dnsRatingTotals = getDnsServerRatingTotals();
  const protectionTotals = getProtectionCenterTotals();
  const blockedProtectionTotal = getBlockedProtectionTotal();

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

        </section>

        <section className="panel protection-center-panel" id="protection">
          <div className="panel-header protection-center-header">
            <div>
              <span className="mini-label">{t('label.protectionLegacyModern')}</span>
              <h2>{t('section.modules.title')}</h2>
              <p>{t('section.modules.description')}</p>
            </div>
            <div className="protection-summary-grid" aria-label="Schutz Parity-Übersicht">
              <span><b>{protectionTotals.legacyStates}</b> alte States</span>
              <span><b>{protectionTotals.endpoints}</b> Endpunkte</span>
              <span><b>{protectionTotals.modules}</b> Module</span>
              <span><b>{formatNumber(blockedProtectionTotal)}</b> Treffer heute</span>
            </div>
          </div>

          <div className="protection-center-layout">
            <div className="protection-main-stack">
              <article className="protection-modules-card">
                <div className="card-title-row">
                  <div>
                    <h3>Filtermodule</h3>
                    <p>Alt: `filteroverview` mit Domain-/Pattern-Blockern, Geräteabdeckung, DNS/SSL-Abhängigkeiten und Blockzählern.</p>
                  </div>
                  <span className="status-chip online">DNS + Pattern aktiv</span>
                </div>
                <div className="protection-module-grid">
                  {protectionModulesModern.map((module) => (
                    <div className={`protection-module-row ${module.type}`} key={module.id}>
                      <strong>{module.label}</strong>
                      <span>{module.type} · {module.category}</span>
                      <b>{formatNumber(module.blockedToday)}</b>
                      <em>{module.usedByDevices} Geräte</em>
                      <code>{module.legacyTemplate}</code>
                      <small>{module.needsSsl ? 'HTTPS nötig' : module.needsDns ? 'DNS nötig' : 'direkt'}</small>
                    </div>
                  ))}
                </div>
              </article>

              <article className="filter-list-card">
                <div className="card-title-row">
                  <div>
                    <h3>Filterlisten</h3>
                    <p>Alt: `filter-details` mit Built-in/Custom, Domains, Name/Beschreibung, Update und CRUD.</p>
                  </div>
                  <button className="ghost-button small" type="button">Liste anlegen</button>
                </div>
                <div className="filter-list-grid">
                  {filterListRows.map((row) => (
                    <div className={`filter-list-row ${row.builtin ? 'builtin' : 'custom'}`} key={row.id}>
                      <strong>{row.name}</strong>
                      <span>{row.type}</span>
                      <span>{formatNumber(row.domains)} Domains</span>
                      <span>{row.lastUpdate}</span>
                      <em>{row.editable ? 'editierbar' : 'Built-in geschützt'}</em>
                    </div>
                  ))}
                </div>
              </article>

              <article className="analysis-card">
                <div className="card-title-row">
                  <div>
                    <h3>Filteranalyse & Doctor</h3>
                    <p>Alt: `filteranalysis`, `analysisdetails` und `doctor` mit Recorder, What-if, CSV, Details und Diagnose.</p>
                  </div>
                  <span className="status-chip warning">What-if aktiv</span>
                </div>
                <div className="analysis-grid">
                  <span><b>{analysisRecorder.device}</b> Analysegerät</span>
                  <span><b>{analysisRecorder.timeLimitSeconds}s</b> Zeitlimit</span>
                  <span><b>{Math.round(analysisRecorder.sizeLimitBytes / 1048576)}MB</b> Größenlimit</span>
                  <span><b>{analysisRecorder.recordedTransactions}</b> Transaktionen</span>
                </div>
                <div className="doctor-list">
                  {doctorProbeRows.map((probe) => (
                    <div className={`doctor-row severity-${probe.severity.toLowerCase()}`} key={probe.tag}>
                      <span>{probe.severity}</span>
                      <strong>{probe.message}</strong>
                      <small>{probe.audience}</small>
                    </div>
                  ))}
                </div>
              </article>
            </div>

            <div className="protection-side-stack">
              <article className="privacy-settings-card">
                <h3>Erweiterte Privatsphäre</h3>
                <p>Alt: `advancedsettings`, `anonymizationstate`, CloakingService und anonyme Schutzdienste.</p>
                <div className="privacy-setting-grid">
                  {advancedPrivacySettings.map((setting) => (
                    <div className={`privacy-setting-row ${setting.state}`} key={setting.id}>
                      <strong>{setting.label}</strong>
                      <span>{setting.value}</span>
                      <code>{setting.endpoint}</code>
                      <em>{setting.warnsIfSslDisabled ? 'SSL-Hinweis' : 'kein SSL-Hinweis'}</em>
                    </div>
                  ))}
                </div>
              </article>

              <article className="tor-card">
                <h3>Tor Exit Nodes</h3>
                <p>Alt: `tor` mit Auto/Manuell-Modus, Länderliste, Suche, Löschen, Warnungen und neuer Identität.</p>
                <div className="tor-summary">
                  <span><b>{torExitPolicy.mode}</b> Modus</span>
                  <span><b>{torExitPolicy.availableCountries}</b> Länder verfügbar</span>
                  <span><b>{torExitPolicy.showWarnings ? 'Ein' : 'Aus'}</b> Warnungen</span>
                </div>
                <div className="warning-strip">
                  {torExitPolicy.selectedCountries.map((country) => <span key={country}>{country}</span>)}
                </div>
                <button className="ghost-button" type="button">Neue Tor-Identität anfordern</button>
              </article>

              <article className="protection-api-card">
                <h3>API-Migration</h3>
                <p>FilterService, Advanced-Privacy-Services, TorService, AnalysisToolService und DoctorService als `/api/v1`-Ziele.</p>
                <div className="endpoint-list protection-endpoints">
                  {protectionCenterEndpoints.map((endpoint) => (
                    <div className="endpoint-row" key={`${endpoint.method}-${endpoint.legacy}-${endpoint.modern}`}>
                      <span>{endpoint.method}</span>
                      <code>{endpoint.legacy}</code>
                      <b>→</b>
                      <code>{endpoint.modern}</code>
                    </div>
                  ))}
                </div>
              </article>
            </div>
          </div>

          <div className="device-capability-strip">
            {protectionCenterCapabilities.map((capability) => (
              <span key={capability}>{capability}</span>
            ))}
          </div>
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

        <section className="panel network-center-panel" id="network">
          <div className="panel-header network-center-header">
            <div>
              <span className="mini-label">{t('label.networkLegacyModern')}</span>
              <h2>{t('section.network.title')}</h2>
              <p>{t('section.network.description')}</p>
            </div>
            <div className="network-summary-grid" aria-label="Netzwerk/DNS Parity-Übersicht">
              <span><b>{networkTotals.legacyStates}</b> alte States</span>
              <span><b>{networkTotals.endpoints}</b> Endpunkte</span>
              <span><b>{networkTotals.dnsServers}</b> DNS-Server</span>
              <span><b>{networkTotals.localRecords}</b> lokale Records</span>
            </div>
          </div>

          <div className="network-center-layout">
            <div className="network-main-stack">
              <article className="network-dns-status-card">
                <div className="card-title-row">
                  <div>
                    <h3>DNS-Status & Resolver</h3>
                    <p>Alt: `dnsstatus` mit Schalter, DHCP/Tor/Custom-Modus und Cache-Flush.</p>
                  </div>
                  <span className="status-chip online">DNS aktiv</span>
                </div>
                <div className="resolver-mode-grid">
                  {dnsResolverModes.map((mode) => (
                    <div className={`resolver-mode-card ${mode.id}`} key={mode.id}>
                      <strong>{mode.label}</strong>
                      <p>{mode.description}</p>
                      <code>{mode.legacyTemplate}</code>
                    </div>
                  ))}
                </div>
                <div className="device-action-row">
                  <button className="primary-button" type="button">DNS-Cache leeren</button>
                  <button className="ghost-button" type="button">Zur Custom-Liste</button>
                  <button className="ghost-button" type="button">Resolver-Modus speichern</button>
                </div>
              </article>

              <article className="network-dns-server-card">
                <div className="card-title-row">
                  <div>
                    <h3>DNS-Serverliste</h3>
                    <p>Alt: `dnsserver` mit Suche, Sortierung, Bulk-Löschen, OrderNumber, Stats und Rating.</p>
                  </div>
                  <div className="dns-rating-summary">
                    <span>{dnsRatingTotals.good} gut</span>
                    <span>{dnsRatingTotals.medium} mittel</span>
                    <span>{dnsRatingTotals.bad} schlecht</span>
                  </div>
                </div>
                <div className="dns-server-table">
                  <div className="dns-server-row dns-server-head">
                    <span>Reihenfolge</span>
                    <span>Server</span>
                    <span>Antwortzeit</span>
                    <span>Zuverlässigkeit</span>
                    <span>Bewertung</span>
                  </div>
                  {dnsServerRows.map((server) => (
                    <div className={`dns-server-row rating-${server.rating.toLowerCase()}`} key={server.server}>
                      <span>{server.orderNumber}</span>
                      <span className="mono">{server.server}</span>
                      <span>{server.responseTimeRating} · {server.responseTimeAverageMs}ms</span>
                      <span>{server.reliabilityRating} · {server.valid}/{server.invalid}/{server.timeout}/{server.error}</span>
                      <span><b>{server.rating}</b></span>
                    </div>
                  ))}
                </div>
              </article>

              <article className="network-record-card">
                <div className="card-title-row">
                  <div>
                    <h3>Lokale DNS-Records</h3>
                    <p>Alt: `dnslocal` mit IPv4/IPv6-Spalten, Built-in-Schutz, Suche, Editieren und Bulk-Löschen.</p>
                  </div>
                  <button className="ghost-button small" type="button">Record hinzufügen</button>
                </div>
                <div className="local-record-grid">
                  {dnsLocalRecords.map((record) => (
                    <div className={`local-record-row ${record.builtin ? 'builtin' : 'custom'}`} key={record.name}>
                      <strong>{record.name}</strong>
                      <span>{record.ipAddress ?? '—'}</span>
                      <span>{record.ip6Address ?? 'IPv6 leer'}</span>
                      <em>{record.builtin ? 'Built-in geschützt' : 'editierbar'}</em>
                    </div>
                  ))}
                </div>
              </article>
            </div>

            <div className="network-side-stack">
              <article className="network-config-card">
                <h3>IPv4 & DHCP</h3>
                <p>Alt: `networksettings` mit Modus, IP, Netzmaske, Gateway, DHCP-Bereich und Lease-Time.</p>
                <div className="network-kv-grid">
                  <span><b>{networkIpv4Config.mode}</b> Modus</span>
                  <span><b>{networkIpv4Config.ipAddress}</b> eBlocker IP</span>
                  <span><b>{networkIpv4Config.networkMask}</b> Netzmaske</span>
                  <span><b>{networkIpv4Config.gateway}</b> Gateway</span>
                  <span><b>{networkIpv4Config.dhcpService}</b> DHCP-Dienst</span>
                  <span><b>{networkIpv4Config.dhcpRangeFirst}–{networkIpv4Config.dhcpRangeLast}</b> DHCP-Bereich</span>
                  <span><b>{networkIpv4Config.dhcpLeaseTimeSeconds / 3600}h</b> Lease-Time</span>
                  <span><b>{networkIpv4Config.advisedNameServer}</b> empfohlener DNS</span>
                </div>
                <div className="dhcp-server-strip">
                  {networkDhcpServers.map((server) => <span key={server}>{server}</span>)}
                </div>
              </article>

              <article className="network-config-card ipv6-card">
                <h3>IPv6</h3>
                <p>Alt: `networksettingsip6` mit Router Advertisements, Privacy Extensions, lokalen/globalen Adressen und Warnungen.</p>
                <div className="network-kv-grid two-col">
                  <span><b>{networkIpv6Config.routerAdvertisementsEnabled ? 'Ein' : 'Aus'}</b> Router Advertisements</span>
                  <span><b>{networkIpv6Config.privacyExtensionsEnabled ? 'Ein' : 'Aus'}</b> Privacy Extensions</span>
                  <span><b>{networkIpv6Config.localAddresses.join(', ')}</b> Link-local</span>
                  <span><b>{networkIpv6Config.globalAddresses.length || 'Keine'}</b> globale Adressen</span>
                </div>
                <div className="warning-strip">
                  {networkIpv6Config.warnings.map((warning) => <span key={warning}>{warning}</span>)}
                </div>
              </article>

              <article className="network-api-card">
                <h3>API-Migration</h3>
                <p>DnsService und NetworkService werden auf neue `/api/v1`-Ziele gemappt.</p>
                <div className="endpoint-list compact-endpoints">
                  {networkCenterEndpoints.map((endpoint) => (
                    <div className="endpoint-row" key={`${endpoint.method}-${endpoint.legacy}-${endpoint.modern}`}>
                      <span>{endpoint.method}</span>
                      <code>{endpoint.legacy}</code>
                      <b>→</b>
                      <code>{endpoint.modern}</code>
                    </div>
                  ))}
                </div>
              </article>
            </div>
          </div>

          <div className="network-wizard-grid">
            {networkWizardFlows.map((flow) => (
              <article className="network-wizard-card" key={flow.id}>
                <strong>{flow.title}</strong>
                <p>{flow.dhcpCheck}</p>
                <div>{flow.legacySteps.map((step) => <span key={step}>{step}</span>)}</div>
                <code>{flow.id === 'automatic' ? 'automatic-*.html' : 'individual-*.html + print-settings.template.html'}</code>
              </article>
            ))}
          </div>

          <div className="device-capability-strip">
            {networkCenterCapabilities.map((capability) => (
              <span key={capability}>{capability}</span>
            ))}
          </div>
        </section>

        <section className="dashboard-grid two">
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
