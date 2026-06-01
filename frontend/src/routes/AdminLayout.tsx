import { Outlet } from "react-router-dom";
import { AdminSidebar } from "@/components/admin/AdminSidebar";
import { AdminTopBar } from "@/components/admin/AdminTopBar";
import { AdminStatusBar } from "@/components/admin/AdminStatusBar";
import { EditorChrome } from "@/components/admin/EditorChrome";
import { useProfile } from "@/api/profile";

/** Admin chrome — replaces admin/layout.tsx. profileName comes from live data. */
export function AdminLayout() {
  const { data: profile } = useProfile();
  const profileName = profile?.name ?? "Admin";

  return (
    <div className="flex flex-col h-screen bg-terminal-bg text-text-primary">
      <AdminTopBar profileName={profileName} />
      <div className="flex-1 flex overflow-hidden">
        <AdminSidebar />
        <EditorChrome>
          <main className="p-4 pt-16 md:p-6">
            <Outlet />
          </main>
        </EditorChrome>
      </div>
      <AdminStatusBar />
    </div>
  );
}
