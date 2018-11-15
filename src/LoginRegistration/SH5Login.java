package LoginRegistration;

import db.DataBase;
import db.User;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "SH5Login", urlPatterns = {"/login"})
public class SH5Login extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String access_token = request.getParameter("access_token");
        String image = request.getParameter("image");
        String username = request.getParameter("first_name");
        String email = request.getParameter("email");


        DataBase db = new DataBase();
        User u = db.retrieveUser(email);

        if (u != null) {
            request.getServletContext().setAttribute("room", u.getRoomID());
            request.getServletContext().setAttribute("user", u);
            response.getWriter().print("message-board");
        } else {
            response.getWriter().print("registration");
        }

        request.getServletContext().setAttribute("access_token", access_token);
        request.getServletContext().setAttribute("image", image);
        request.getServletContext().setAttribute("name", username);
        request.getServletContext().setAttribute("email", email);
        request.getServletContext().setAttribute("user", u);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        RequestDispatcher view = request.getRequestDispatcher("html/login.html");
        view.forward(request, response);
    }
}
