export type MigrationStatus = 'foundation' | 'api-contract' | 'legacy-bridge' | 'new-ui-ready';

export interface ModernNavigationItem {
  readonly id: string;
  readonly title: string;
  readonly description: string;
  readonly legacyPath: string;
  readonly targetApiPrefix: string;
  readonly status: MigrationStatus;
}

export interface ModernNavigationSection {
  readonly id: string;
  readonly title: string;
  readonly items: readonly ModernNavigationItem[];
}

export const modernNavigationSections: readonly ModernNavigationSection[] = [
  {
    id: 'overview',
    title: 'Overview',
    items: [
      {
        id: 'system-status',
        title: 'System status',
        description: 'Health, services, updates and diagnostics for the eBlocker appliance.',
        legacyPath: '/settings/#!/system/status',
        targetApiPrefix: '/api/v1/system',
        status: 'foundation'
      },
      {
        id: 'devices',
        title: 'Devices',
        description: 'Known clients, pause state, DNS filtering and parental-control assignments.',
        legacyPath: '/settings/#!/devices/list',
        targetApiPrefix: '/api/v1/devices',
        status: 'api-contract'
      }
    ]
  },
  {
    id: 'protection',
    title: 'Protection',
    items: [
      {
        id: 'dns-filtering',
        title: 'DNS and filtering',
        description: 'Resolvers, local DNS records, blocklists, allowlists and encrypted-DNS migration.',
        legacyPath: '/settings/#!/dns/status',
        targetApiPrefix: '/api/v1/dns',
        status: 'api-contract'
      },
      {
        id: 'ssl',
        title: 'HTTPS and certificates',
        description: 'Root CA, trusted apps, trusted domains and TLS diagnostics.',
        legacyPath: '/settings/#!/https/status',
        targetApiPrefix: '/api/v1/ssl',
        status: 'legacy-bridge'
      },
      {
        id: 'vpn',
        title: 'VPN and mobile',
        description: 'OpenVPN compatibility plus the future WireGuard-first management UI.',
        legacyPath: '/settings/#!/mobile',
        targetApiPrefix: '/api/v1/vpn',
        status: 'legacy-bridge'
      }
    ]
  },
  {
    id: 'family',
    title: 'Family controls',
    items: [
      {
        id: 'parental-control',
        title: 'Parental control',
        description: 'Users, profiles, time budgets, access restrictions and reporting.',
        legacyPath: '/settings/#!/parentalcontrol/users',
        targetApiPrefix: '/api/v1/parental-control',
        status: 'legacy-bridge'
      }
    ]
  }
];

export function getLegacyFallbackPath(item: ModernNavigationItem): string {
  return item.legacyPath || '/settings/';
}

export function countItemsByStatus(status: MigrationStatus): number {
  return modernNavigationSections.reduce((count, section) => {
    return count + section.items.filter((item) => item.status === status).length;
  }, 0);
}

export function getAllModernNavigationItems(): readonly ModernNavigationItem[] {
  return modernNavigationSections.flatMap((section) => section.items);
}
