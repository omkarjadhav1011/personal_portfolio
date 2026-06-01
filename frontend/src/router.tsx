import { createBrowserRouter } from "react-router-dom";
import { RootLayout } from "@/routes/RootLayout";
import { RouteError } from "@/routes/RouteError";
import { NotFound } from "@/routes/NotFound";
import Home from "@/pages/Home";
import ScratchProjects from "@/pages/ScratchProjects";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <Home /> },
      { path: "scratch", element: <ScratchProjects /> },
      { path: "*", element: <NotFound /> },
    ],
  },
]);
