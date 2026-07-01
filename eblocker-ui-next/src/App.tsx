import { countItemsByStatus, modernNavigationSections } from './domain/navigation';

const statusLabels = {
  foundation: 'Foundation',
  'api-contract': 'API contract next',
  'legacy-bridge': 'Legacy bridge',
  'new-ui-ready': 'Modern UI ready'
} as const;

function App() {
  return (
    <main className="app-shell">
      <section className="hero-panel">
        <p className="eyebrow">eBlocker Community</p>
        <h1>Modern UI migration shell</h1>
        <p className="hero-copy">
          This React/TypeScript UI is the new migration target. It starts as a safe parallel
          package and keeps explicit links back to the legacy AngularJS screens until each feature
          has a typed API contract and complete modern replacement.
        </p>
        <div className="status-grid" aria-label="Migration status summary">
          <div>
            <strong>{countItemsByStatus('foundation')}</strong>
            <span>foundation views</span>
          </div>
          <div>
            <strong>{countItemsByStatus('api-contract')}</strong>
            <span>API contracts next</span>
          </div>
          <div>
            <strong>{countItemsByStatus('legacy-bridge')}</strong>
            <span>legacy bridges</span>
          </div>
        </div>
      </section>

      <section className="section-grid" aria-label="Modern UI migration areas">
        {modernNavigationSections.map((section) => (
          <article className="section-card" key={section.id}>
            <h2>{section.title}</h2>
            <div className="feature-list">
              {section.items.map((item) => (
                <a className="feature-card" href={item.legacyPath} key={item.id}>
                  <span className={`status-pill status-${item.status}`}>{statusLabels[item.status]}</span>
                  <h3>{item.title}</h3>
                  <p>{item.description}</p>
                  <code>{item.targetApiPrefix}</code>
                </a>
              ))}
            </div>
          </article>
        ))}
      </section>
    </main>
  );
}

export default App;
