import { DriveClient } from "@/components/admin/DriveClient";

/** Secure Document Vault admin page. The client owns folder navigation + its own queries. */
export default function DriveAdmin() {
  return <DriveClient />;
}
