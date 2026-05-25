import { useCallback, useEffect, useState } from "react";
import { Bell, Check, ChevronLeft, ChevronRight, ExternalLink, Settings } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageError } from "@/components/ui/PageError";
import { Pill } from "@/components/ui/Badge";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { PageHeader } from "@/components/layout/PageHeader";
import { ApiError } from "@/lib/api";
import {
  type NotificationPage,
  type UserNotificationResponse,
  listNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
} from "@/lib/api";
import { formatShortDateTime } from "@/lib/format";

function typePillVariant(type: string): "rose" | "amber" | "green" | "blue" {
  switch (type) {
    case "alert":
      return "rose";
    case "warning":
      return "amber";
    case "success":
      return "green";
    default:
      return "blue";
  }
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<UserNotificationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const navigate = useNavigate();

  const fetchNotifications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data: NotificationPage = await listNotifications(page, 20);
      setNotifications(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unknown error");
      setNotifications([]);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void fetchNotifications();
  }, [fetchNotifications]);

  async function handleMarkAsRead(id: string) {
    await markNotificationAsRead(id);
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true, readAt: new Date().toISOString() } : n)),
    );
  }

  async function handleMarkAllAsRead() {
    await markAllNotificationsAsRead();
    setNotifications((prev) =>
      prev.map((n) => ({ ...n, read: true, readAt: new Date().toISOString() })),
    );
  }

  const headerActions = (
    <>
      <Button variant="secondary" size="sm" className="gap-2" asChild>
        <Link to="/app/notification-preferences">
          <Settings className="h-4 w-4" aria-hidden />
          Preferences
        </Link>
      </Button>
      {notifications.some((n) => !n.read) ? (
        <Button variant="secondary" size="sm" className="gap-2" onClick={() => void handleMarkAllAsRead()}>
          <Check className="h-4 w-4" aria-hidden />
          Mark all read
        </Button>
      ) : null}
    </>
  );

  if (loading && page === 0 && notifications.length === 0 && !error) {
    return (
      <div className="space-y-6">
        <PageHeader
          icon={Bell}
          iconAccent="from-amber-500 to-orange-600 shadow-amber-500/20 ring-amber-400/20"
          title="Notifications"
          description="All your in-app alerts in one place."
          actions={headerActions}
        />
        <TableSkeleton headers={["Time", "Type", "Notification", "Status", "Actions"]} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={Bell}
        iconAccent="from-amber-500 to-orange-600 shadow-amber-500/20 ring-amber-400/20"
        title="Notifications"
        description="All your in-app alerts in one place."
        actions={headerActions}
      />

      {error ? (
        <PageError title="Could not load notifications" message={error} onRetry={() => void fetchNotifications()} />
      ) : null}

      {!error && notifications.length === 0 && !loading ? (
        <EmptyState
          icon={<Bell className="h-12 w-12 text-neutral-300 dark:text-neutral-600" />}
          title="No notifications"
          description="Configure alert rules to receive notifications when workflows fail or complete."
          action={
            <Button onClick={() => navigate("/app/alert-rules")}>Configure alerts</Button>
          }
        />
      ) : null}

      {!error && notifications.length > 0 ? (
        <>
          <DataTable>
            <table className="table-data">
              <thead>
                <tr>
                  <th className="w-44">Time</th>
                  <th className="w-28">Type</th>
                  <th>Notification</th>
                  <th className="w-28">Status</th>
                  <th className="w-28 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {notifications.map((notification) => (
                  <tr key={notification.id} className={!notification.read ? "bg-blue-50/40 dark:bg-blue-950/20" : undefined}>
                    <td className="text-neutral-500">{formatShortDateTime(notification.createdAt)}</td>
                    <td>
                      <Pill variant={typePillVariant(notification.type)} className="capitalize">
                        {notification.type}
                      </Pill>
                    </td>
                    <td>
                      <p className="font-medium text-neutral-900 dark:text-neutral-100">{notification.title}</p>
                      {notification.message ? (
                        <p className="mt-0.5 line-clamp-2 text-neutral-500">{notification.message}</p>
                      ) : null}
                    </td>
                    <td>{notification.read ? "Read" : "Unread"}</td>
                    <td>
                      <div className="flex justify-end gap-1">
                        {notification.linkUrl ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            aria-label="Open linked resource"
                            onClick={() => {
                              if (notification.linkUrl!.startsWith("/")) {
                                navigate(notification.linkUrl!);
                              } else {
                                window.open(notification.linkUrl!, "_blank", "noopener,noreferrer");
                              }
                            }}
                          >
                            <ExternalLink className="h-4 w-4" />
                          </Button>
                        ) : null}
                        {!notification.read ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            aria-label="Mark as read"
                            onClick={() => void handleMarkAsRead(notification.id)}
                          >
                            <Check className="h-4 w-4" />
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DataTable>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-neutral-500">
              Showing {notifications.length} of {totalElements} notifications
            </p>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                aria-label="Previous page"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="min-w-[7rem] text-center text-sm text-neutral-600 dark:text-neutral-400">
                Page {page + 1} of {totalPages || 1}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                aria-label="Next page"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
