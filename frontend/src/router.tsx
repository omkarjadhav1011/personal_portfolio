import { createBrowserRouter } from "react-router-dom";
import { RootLayout } from "@/routes/RootLayout";
import { MainLayout } from "@/routes/MainLayout";
import { RouteError } from "@/routes/RouteError";
import { NotFound } from "@/routes/NotFound";
import Home from "@/pages/Home";
import ProjectDetail from "@/pages/ProjectDetail";
import Login from "@/pages/admin/Login";
import AdminHome from "@/pages/admin/AdminHome";
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
      { path: "admin/login", element: <Login /> },
      { path: "admin", element: <AdminHome /> },
      { path: "scratch", element: <ScratchProjects /> },
      { path: "*", element: <NotFound /> },
    ],
  },
]);
