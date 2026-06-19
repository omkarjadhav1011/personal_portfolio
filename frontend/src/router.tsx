import { lazy } from "react";
import { createBrowserRouter } from "react-router-dom";
import { RootLayout } from "@/routes/RootLayout";
import { MainLayout } from "@/routes/MainLayout";
import { RequireAuth, RedirectIfAuthed } from "@/routes/RequireAuth";
import { RouteError } from "@/routes/RouteError";
import { NotFound } from "@/routes/NotFound";
import Home from "@/pages/Home";

// Lazy-loaded routes keep admin + recruiter + detail/scratch out of the initial
// public bundle (Suspense fallbacks live in RootLayout/MainLayout/AdminLayout).
const ProjectDetail = lazy(() => import("@/pages/ProjectDetail"));
const RecruiterPage = lazy(() => import("@/pages/RecruiterPage"));
const ScratchProjects = lazy(() => import("@/pages/ScratchProjects"));
const Login = lazy(() => import("@/pages/admin/Login"));
const OAuthCallback = lazy(() => import("@/pages/admin/OAuthCallback"));
const AdminLayout = lazy(() =>
  import("@/routes/AdminLayout").then((m) => ({ default: m.AdminLayout })),
);
const Dashboard = lazy(() => import("@/pages/admin/Dashboard"));
const ProjectsAdmin = lazy(() => import("@/pages/admin/ProjectsAdmin"));
const ExperienceAdmin = lazy(() => import("@/pages/admin/ExperienceAdmin"));
const SkillsAdmin = lazy(() => import("@/pages/admin/SkillsAdmin"));
const ProfileAdmin = lazy(() => import("@/pages/admin/ProfileAdmin"));

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
          { path: "recruiter", element: <RecruiterPage /> },
        ],
      },
      {
        path: "admin/login",
        element: <RedirectIfAuthed />,
        children: [{ index: true, element: <Login /> }],
      },
      // OAuth2 redirect target. Sibling of admin/login — NOT under RequireAuth (runs before
      // a token exists) and NOT under RedirectIfAuthed (it is what sets the token).
      { path: "admin/oauth/callback", element: <OAuthCallback /> },
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
