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
import ExperienceAdmin from "@/pages/admin/ExperienceAdmin";
import SkillsAdmin from "@/pages/admin/SkillsAdmin";
import ProfileAdmin from "@/pages/admin/ProfileAdmin";
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
              { path: "experience", element: <ExperienceAdmin /> },
              { path: "skills", element: <SkillsAdmin /> },
              { path: "profile", element: <ProfileAdmin /> },
            ],
          },
        ],
      },
      { path: "scratch", element: <ScratchProjects /> },
      { path: "*", element: <NotFound /> },
    ],
  },
]);
