import { useState, useEffect } from "react";
import { Bell, Check, ChevronLeft, ChevronRight, ExternalLink, Settings } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/Toast";
import {
  type UserNotificationResponse,
  type NotificationPage,
  listNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
} from "@/lib/api";

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<UserNotificationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const { addToast } = useToast();
  const navigate = useNavigate();

  async function fetchNotifications() {
    setLoading(true);
    try {
      const data: NotificationPage = await listNotifications(page, 20);
      setNotifications(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to load notifications",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchNotifications();
  }, [page]);

  async function handleMarkAsRead(id: string) {
    try {
      await markNotificationAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true, readAt: new Date().toISOString() } : n)),
      );
      addToast({ type: "success", title: "Notification marked as read" });
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to mark as read",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    }
  }

  async function handleMarkAllAsRead() {
    try {
      await markAllNotificationsAsRead();
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, read: true, readAt: new Date().toISOString() })),
      );
      addToast({ type: "success", title: "All notifications marked as read" });
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to mark all as read",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    }
  }

  function formatTimestamp(ts: string): string {
    return new Date(ts).toLocaleString();
  }

  function getTypeStyles(type: string): string {
    switch (type) {
      case "alert":
        return "bg-red-100 text-red-800";
      case "warning":
        return "bg-yellow-100 text-yellow-800";
      case "success":
        return "bg-green-100 text-green-800";
      default:
        return "bg-blue-100 text-blue-800";
    }
  }

  if (loading && page === 0) {
    return (
      <div className="p-6 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Notifications</h1>
        </div>
        <TableSkeleton headers={["Time", "Type", "Title", "Message", "Actions"]} />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Notifications</h1>
          <p className="text-sm text-muted-foreground mt-1">
            All your notifications in one place
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Button variant="secondary" asChild>
            <Link to="/app/notification-preferences">
              <Settings className="w-4 h-4 mr-2" />
              Preferences
            </Link>
          </Button>
          {notifications.some((n) => !n.read) && (
            <Button variant="secondary" onClick={handleMarkAllAsRead}>
              <Check className="w-4 h-4 mr-2" />
              Mark All as Read
            </Button>
          )}
        </div>
      </div>

      {notifications.length === 0 ? (
        <EmptyState
          icon={<Bell className="w-12 h-12 text-muted-foreground" />}
          title="No notifications"
          description="You don't have any notifications yet. Configure alert rules to receive notifications."
          action={
            <Button onClick={() => navigate("/app/alert-rules")}>
              Configure Alerts
            </Button>
          }
        />
      ) : (
        <>
          <DataTable>
            <table className="w-full">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground w-44">
                    Time
                  </th>
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground w-24">
                    Type
                  </th>
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground">
                    Notification
                  </th>
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground w-24">
                    Status
                  </th>
                  <th className="text-right py-3 px-4 font-medium text-muted-foreground w-28">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {notifications.map((notification) => (
                  <tr
                    key={notification.id}
                    className={`border-b border-border hover:bg-muted/50 ${
                      !notification.read ? "bg-primary/5" : ""
                    }`}
                  >
                    <td className="py-3 px-4 text-sm text-muted-foreground">
                      {formatTimestamp(notification.createdAt)}
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded text-xs font-medium capitalize ${getTypeStyles(notification.type)}`}
                      >
                        {notification.type}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <div className="font-medium">{notification.title}</div>
                      {notification.message && (
                        <div className="text-sm text-muted-foreground mt-0.5 line-clamp-2">
                          {notification.message}
                        </div>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      {notification.read ? (
                        <span className="text-sm text-muted-foreground">Read</span>
                      ) : (
                        <span className="text-sm font-medium text-primary">Unread</span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex justify-end gap-2">
                        {notification.linkUrl && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              if (notification.linkUrl!.startsWith("/")) {
                                navigate(notification.linkUrl!);
                              } else {
                                window.open(notification.linkUrl!, "_blank");
                              }
                            }}
                            title="View details"
                          >
                            <ExternalLink className="w-4 h-4" />
                          </Button>
                        )}
                        {!notification.read && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleMarkAsRead(notification.id)}
                            title="Mark as read"
                          >
                            <Check className="w-4 h-4" />
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DataTable>

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">
              Showing {notifications.length} of {totalElements} notifications
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <span className="text-sm">
                Page {page + 1} of {totalPages || 1}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
