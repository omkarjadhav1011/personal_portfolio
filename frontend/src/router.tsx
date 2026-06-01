import { createBrowserRouter } from "react-router-dom";
import { RootLayout } from "@/routes/RootLayout";
import { MainLayout } from "@/routes/MainLayout";
import { RequireAuth, RedirectIfAuthed } from "@/routes/RequireAuth";
import { RouteError } from "@/routes/RouteError";
import { NotFound } from "@/routes/NotFound";
import Home from "@/pages/Home";
import ProjectDetail from "@/pages/ProjectDetail";
import Login from "@/pages/admin/Login";
import { AdminLayout } from "@/routes/AdminLayout";
import Dashboard from "@/pages/admin/Dashboard";
import ProjectsAdmin from "@/pages/admin/ProjectsAdmin";
import ComingSoon from "@/pages/admin/ComingSoon";
import ScratchProjects from "@/pages/ScratchProjects";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      {
        element: <MainLayout />,
        children: [
          { index: true, element: <Home /> },
          { path: "projects/:slug", element: <ProjectDetail /> },
        ],
      },
      {
        path: "admin/login",
        element: <RedirectIfAuthed />,
        children: [{ index: true, element: <Login /> }],
      },
      {
        path: "admin",
        element: <RequireAuth />,
        children: [
          {
            element: <AdminLayout />,
            children: [
              { index: true, element: <Dashboard /> },
              { path: "projects", element: <ProjectsAdmin /> },
              { path: "experience", element: <ComingSoon /> },
              { path: "skills", element: <ComingSoon /> },
              { path: "profile", element: <ComingSoon /> },
            ],
          },
        ],
      },
      { path: "scratch", element: <ScratchProjects /> },
      { path: "*", element: <NotFound /> },
    ],
  },
]);
