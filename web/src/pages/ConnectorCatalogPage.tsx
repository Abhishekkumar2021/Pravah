import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Database,
  File,
  Globe,
  Loader2,
  Radio,
  RefreshCw,
  Search,
  Server,
  Zap,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Pill } from "@/components/ui/Badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import {
  listConnectors,
  type ConnectorSpec,
  type ConnectorType,
} from "@/lib/api";

function getConnectorIcon(type: ConnectorType) {
  switch (type) {
    case "DATABASE":
      return <Database className="h-5 w-5" />;
    case "FILE":
      return <File className="h-5 w-5" />;
    case "PROTOCOL":
      return <Globe className="h-5 w-5" />;
    case "STREAMING":
      return <Radio className="h-5 w-5" />;
    case "SAAS":
      return <Server className="h-5 w-5" />;
    case "CDC":
      return <Zap className="h-5 w-5" />;
    default:
      return <Database className="h-5 w-5" />;
  }
}

function getModeVariant(mode: string): "blue" | "green" | "amber" {
  switch (mode) {
    case "SOURCE":
      return "blue";
    case "SINK":
      return "green";
    case "BIDIRECTIONAL":
      return "amber";
    default:
      return "blue";
  }
}

function ConnectorCard({ connector }: { connector: ConnectorSpec }) {
  return (
    <Card className="group relative overflow-hidden p-4 transition-all hover:shadow-md dark:hover:shadow-neutral-800/50">
      <div className="flex items-start gap-4">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-neutral-600 transition-colors group-hover:bg-neutral-200 dark:bg-neutral-800 dark:text-neutral-400 dark:group-hover:bg-neutral-700">
          {getConnectorIcon(connector.type)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h3 className="truncate font-semibold text-neutral-900 dark:text-neutral-100">
              {connector.name}
            </h3>
            <Pill variant={getModeVariant(connector.mode)} className="shrink-0">
              {connector.mode === "BIDIRECTIONAL" ? "Both" : connector.mode.toLowerCase()}
            </Pill>
          </div>
          <p className="mt-1 line-clamp-2 text-sm text-neutral-600 dark:text-neutral-400">
            {connector.description}
          </p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {connector.tags.slice(0, 4).map((tag) => (
              <span
                key={tag}
                className="inline-flex rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-600 dark:bg-neutral-800 dark:text-neutral-400"
              >
                {tag}
              </span>
            ))}
            {connector.tags.length > 4 && (
              <span className="inline-flex rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500 dark:bg-neutral-800 dark:text-neutral-500">
                +{connector.tags.length - 4}
              </span>
            )}
          </div>
        </div>
      </div>
      <div className="mt-4 flex items-center justify-between border-t border-neutral-100 pt-3 dark:border-neutral-800">
        <span className="text-xs text-neutral-500">{connector.category}</span>
        <span className="text-xs text-neutral-400">v{connector.version}</span>
      </div>
    </Card>
  );
}

export function ConnectorCatalogPage() {
  const [connectors, setConnectors] = useState<ConnectorSpec[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [activeTab, setActiveTab] = useState<string>("all");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await listConnectors();
      setConnectors(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    let result = connectors;

    // Filter by tab
    if (activeTab !== "all") {
      result = result.filter((c) => c.type === activeTab);
    }

    // Filter by search
    const q = search.trim().toLowerCase();
    if (q) {
      result = result.filter(
        (c) =>
          c.name.toLowerCase().includes(q) ||
          c.description.toLowerCase().includes(q) ||
          c.tags.some((t) => t.toLowerCase().includes(q)),
      );
    }

    return result;
  }, [connectors, activeTab, search]);

  const counts = useMemo(() => {
    const result: Record<string, number> = { all: connectors.length };
    for (const c of connectors) {
      result[c.type] = (result[c.type] || 0) + 1;
    }
    return result;
  }, [connectors]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="page-title flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 text-white shadow-lg shadow-violet-500/20 ring-1 ring-violet-400/20">
              <Database className="h-5 w-5" />
            </span>
            <span className="bg-gradient-to-r from-neutral-900 to-neutral-700 bg-clip-text text-transparent dark:from-neutral-100 dark:to-neutral-300">
              Connector Catalog
            </span>
          </h1>
          <p className="page-desc mt-2">
            Browse available data connectors for sources and destinations.
          </p>
        </div>

        <Button type="button" variant="secondary" onClick={() => void load()} disabled={loading}>
          <RefreshCw className="mr-2 h-4 w-4" aria-hidden />
          Refresh
        </Button>
      </div>

      <Card className="p-4">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search connectors by name, description, or tags…"
            aria-label="Search connectors"
            className="pl-10"
          />
        </div>
      </Card>

      {error && (
        <p className="text-sm text-rose-600 dark:text-rose-400" role="alert">
          {error}
        </p>
      )}

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList aria-label="Connector types">
          <TabsTrigger value="all" className="gap-1.5">
            All
            <span className="ml-1 rounded-full bg-neutral-200 px-1.5 py-0.5 text-xs dark:bg-neutral-700">
              {counts.all || 0}
            </span>
          </TabsTrigger>
          <TabsTrigger value="DATABASE" className="gap-1.5">
            <Database className="h-3.5 w-3.5" />
            Database
            <span className="ml-1 rounded-full bg-neutral-200 px-1.5 py-0.5 text-xs dark:bg-neutral-700">
              {counts.DATABASE || 0}
            </span>
          </TabsTrigger>
          <TabsTrigger value="FILE" className="gap-1.5">
            <File className="h-3.5 w-3.5" />
            File
            <span className="ml-1 rounded-full bg-neutral-200 px-1.5 py-0.5 text-xs dark:bg-neutral-700">
              {counts.FILE || 0}
            </span>
          </TabsTrigger>
          <TabsTrigger value="PROTOCOL" className="gap-1.5">
            <Globe className="h-3.5 w-3.5" />
            Protocol
            <span className="ml-1 rounded-full bg-neutral-200 px-1.5 py-0.5 text-xs dark:bg-neutral-700">
              {counts.PROTOCOL || 0}
            </span>
          </TabsTrigger>
        </TabsList>

        <TabsContent value={activeTab} className="mt-6">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-neutral-400" />
            </div>
          ) : filtered.length === 0 ? (
            <Card className="p-12 text-center">
              <Database className="mx-auto h-12 w-12 text-neutral-300 dark:text-neutral-600" />
              <h3 className="mt-4 text-lg font-medium text-neutral-900 dark:text-neutral-100">
                No connectors found
              </h3>
              <p className="mt-2 text-sm text-neutral-600 dark:text-neutral-400">
                {search ? "Try a different search term." : "No connectors available in this category."}
              </p>
            </Card>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {filtered.map((connector) => (
                <ConnectorCard key={connector.id} connector={connector} />
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      <Card className="bg-neutral-50 p-4 dark:bg-neutral-900">
        <div className="flex items-start gap-3">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-violet-100 text-violet-600 dark:bg-violet-900/30 dark:text-violet-400">
            <Zap className="h-4 w-4" />
          </div>
          <div>
            <h4 className="font-medium text-neutral-900 dark:text-neutral-100">
              Need a custom connector?
            </h4>
            <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
              Use Python or Container stages to connect to any data source. For production workloads,
              consider building a custom connector plugin.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
