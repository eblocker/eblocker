import {
  activityEvents,
  dashboardMetrics,
  deviceRows,
  familyProfiles,
  getCriticalServiceCount,
  getProtectedDeviceCount,
  getTotalBlockedToday,
  networkCards,
  protectionModules,
  serviceHealth,
  type HealthLevel,
  type ProtectionState
} from './domain/dashboard';
import { t } from './i18n/messages';
import './App.css';

const navItems = [
  { id: 'dashboard', label: t('nav.dashboard'), icon: '⌁' },
  { id: 'devices', label: t('nav.devices'), icon: '◈' },
  { id: 'protection', label: t('nav.protection'), icon: '◆' },
  { id: 'network', label: t('nav.network'), icon: '◎' },
  { id: 'family', label: t('nav.family'), icon: '◌' },
  { id: 'system', label: t('nav.system'), icon: '⚙' }
] as const;

function healthLabel(status: HealthLevel): string {
  if (status === 'online') return t('status.online');
  if (status === 'warning') return t('status.warning');
  return t('status.offline');
}

function protectionLabel(status: ProtectionState): string {
  if (status === 'protected') return t('status.protected');
  if (status === 'learning') return t('status.learning');
  return t('status.paused');
}

function App() {
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

        <section className="hero" id="dashboard">
          <div>
            <p className="eyebrow">{t('app.badge')}</p>
            <h1>{t('app.title')}</h1>
            <p className="hero-copy">{t('app.subtitle')}</p>
          </div>
          <div className="release-card">
            <span>{t('app.release')}</span>
            <strong>{getProtectedDeviceCount()} geschützte Geräte</strong>
            <small>{getTotalBlockedToday().toLocaleString('de-DE')} Anfragen heute blockiert</small>
            <small>{getCriticalServiceCount()} kritische Dienste</small>
          </div>
        </section>

        <section className="section-heading">
          <div>
            <h2>{t('section.overview.title')}</h2>
            <p>{t('section.overview.description')}</p>
          </div>
          <span>{t('copy.noPr')}</span>
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

        <section className="panel" id="devices">
          <div className="panel-header">
            <div>
              <h2>{t('section.devices.title')}</h2>
              <p>{t('section.devices.description')}</p>
            </div>
            <div className="filter-pills">
              <span>{t('status.protected')}</span>
              <span>{t('status.learning')}</span>
              <span>{t('status.paused')}</span>
            </div>
          </div>
          <div className="device-table" role="table" aria-label={t('section.devices.title')}>
            <div className="table-row table-head" role="row">
              <span>{t('table.device')}</span>
              <span>{t('table.ip')}</span>
              <span>{t('table.profile')}</span>
              <span>{t('table.protection')}</span>
              <span>{t('table.blocked')}</span>
              <span>{t('table.status')}</span>
            </div>
            {deviceRows.map((device) => (
              <div className="table-row" role="row" key={device.id}>
                <span className="device-name"><b>{device.name}</b><small>{device.type}</small></span>
                <span className="mono">{device.ipAddress}</span>
                <span>{device.profile}</span>
                <span><em className={`protection-badge ${device.protection}`}>{protectionLabel(device.protection)}</em></span>
                <span>{device.blockedToday.toLocaleString('de-DE')}</span>
                <span><em className={`status-chip ${device.status}`}>{healthLabel(device.status)}</em></span>
              </div>
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

export default App;
